package com.agent.reconciliation.service;

import com.agent.reconciliation.domain.dto.ExtractedInvoice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.model.Media;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Service responsible for multimodal extraction of invoice data using Spring AI.
 * strictly adheres to the invariant that the LLM extracts printed values and never performs math.
 */
@Service
public class InvoiceExtractionService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceExtractionService.class);

    private static final String SYSTEM_PROMPT = """
            You are an expert financial audit and document data extraction assistant specializing in commercial invoices and supplier receipts.
            Your task is to accurately extract structured invoice line items, references, and totals from the attached document.
            
            Strict Invariants:
            1. NO ARITHMETIC / NO MATHEMATICAL CALCULATIONS:
               - NEVER calculate math, sum totals, balance line items, or recalculate taxes or discounts.
               - Extract numerical values STRICTLY as printed on the physical document.
               - If an item total or grand total is printed incorrectly or inconsistently on the invoice, extract the printed value as-is.
            
            2. BILINGUAL EXTRACTION (Arabic & English):
               - Support Arabic and English text seamlessly, specifically Egyptian Arabic commercial and supply chain terminology:
                 * Agricultural commodities: e.g., 'طماطم' (Tomatoes), 'بصل' (Onions), 'بطاطس' (Potatoes), 'خيار' (Cucumbers), 'فلفل' (Peppers), 'ليمون' (Lemons).
                 * Delivery, porterage, and handling charges: e.g., 'مشال' (Porterage / Manual Handling), 'توصيل' (Delivery), 'نقل' / 'شحن' (Freight / Transportation).
                 * Commercial identifiers: e.g., 'أمر توريد' (Purchase Order / PO Reference), 'فاتورة' (Invoice), 'ضريبة' (Tax / VAT).
            
            3. NUMERAL NORMALIZATION:
               - Convert all Arabic-Indic numerals (٠, ١, ٢, ٣, ٤, ٥, ٦, ٧, ٨, ٩) into standard decimal digits (0, 1, 2, 3, 4, 5, 6, 7, 8, 9).
               - Parse all quantities and prices into standard decimal numbers.
            
            4. EXTRA FEES & SURCHARGES:
               - Any freight, delivery, handling, porterage ('مشال'), or unitemized surcharge printed on the invoice must be extracted into `extraFees`.
               - If no additional fees are present, set `extraFees` to 0.00.
            
            5. CANONICAL SKU MAPPING:
               - For recognized standard commodities, suggest the internal canonical SKU code:
                 * Tomatoes / طماطم -> 'SKU-TOMATO-RED'
                 * Red/Yellow Onions / بصل -> 'SKU-ONION-YELLOW'
                 * Potatoes / بطاطس -> 'SKU-POTATO-SPUNTA'
               - If the item does not correspond to a known SKU or is ambiguous, return null for `suggestedSku`.
            
            Output Requirements:
            {format}
            """;

    private final ChatClient chatClient;
    private final BeanOutputConverter<ExtractedInvoice> outputConverter;

    public InvoiceExtractionService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        this.outputConverter = new BeanOutputConverter<>(ExtractedInvoice.class);
    }

    /**
     * Extracts structured invoice data from an uploaded invoice file (PDF/PNG/JPEG).
     *
     * @param file the uploaded invoice file
     * @return structured ExtractedInvoice record
     */
    public ExtractedInvoice extractInvoice(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot extract invoice from empty or null file.");
        }

        try {
            MimeType mimeType = resolveMimeType(file);
            Resource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            Media media = new Media(mimeType, fileResource);

            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model("gpt-4o")
                    .temperature(0.0)
                    .build();

            log.info("Dispatching multimodal extraction request for file: {} ({}) using gpt-4o",
                    file.getOriginalFilename(), mimeType);

            ExtractedInvoice extracted = chatClient.prompt()
                    .options(options)
                    .system(s -> s.text(SYSTEM_PROMPT).param("format", outputConverter.getFormat()))
                    .user(u -> u.text("Please extract all structured invoice data from the attached document.")
                            .media(media))
                    .call()
                    .entity(outputConverter);

            log.info("Successfully extracted invoice: {} for vendor: {}",
                    extracted != null ? extracted.invoiceNumber() : "null",
                    extracted != null ? extracted.vendorName() : "null");

            return extracted;
        } catch (IOException ex) {
            log.error("Failed to read bytes from uploaded invoice file: {}", file.getOriginalFilename(), ex);
            throw new RuntimeException("Failed to read invoice file: " + file.getOriginalFilename(), ex);
        }
    }

    /**
     * Helper to resolve the MimeType from file content type or filename extension.
     */
    private MimeType resolveMimeType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank() && !contentType.equalsIgnoreCase(MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE)) {
            try {
                return MimeTypeUtils.parseMimeType(contentType);
            } catch (Exception ignored) {
                log.warn("Could not parse file content type: {}, falling back to extension inspection.", contentType);
            }
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (originalFilename.endsWith(".png")) {
            return MimeTypeUtils.IMAGE_PNG;
        } else if (originalFilename.endsWith(".jpg") || originalFilename.endsWith(".jpeg")) {
            return MimeTypeUtils.IMAGE_JPEG;
        } else {
            return MimeTypeUtils.parseMimeType("application/pdf");
        }
    }

    public BeanOutputConverter<ExtractedInvoice> getOutputConverter() {
        return outputConverter;
    }
}
