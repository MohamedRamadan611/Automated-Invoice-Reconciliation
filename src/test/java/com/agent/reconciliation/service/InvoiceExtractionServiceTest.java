package com.agent.reconciliation.service;

import com.agent.reconciliation.domain.dto.ExtractedInvoice;
import com.agent.reconciliation.domain.dto.ExtractedLineItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceExtractionServiceTest {

    @Mock
    private ChatModel chatModel;

    private InvoiceExtractionService invoiceExtractionService;

    @BeforeEach
    void setUp() {
        ChatClient.Builder chatClientBuilder = ChatClient.builder(chatModel);
        invoiceExtractionService = new InvoiceExtractionService(chatClientBuilder);
    }

    @Test
    @DisplayName("Should parse Arabic receipt JSON into ExtractedInvoice with zero null fields and exact values")
    void shouldExtractArabicReceiptSuccessfully() {
        String arabicReceiptJson = """
                {
                  "invoiceNumber": "INV-2026-099",
                  "poReference": "PO-2026-001",
                  "vendorName": "شركة الأهرام للتوريدات الغذائية",
                  "items": [
                    {
                      "vendorItemDescription": "طماطم بلدي طازجة فاخرة",
                      "quantity": 100.00,
                      "unitPrice": 15.50,
                      "lineTotal": 1550.00,
                      "suggestedSku": "SKU-TOMATO-RED"
                    },
                    {
                      "vendorItemDescription": "بصل أحمر درجة أولى",
                      "quantity": 50.00,
                      "unitPrice": 12.00,
                      "lineTotal": 600.00,
                      "suggestedSku": "SKU-ONION-YELLOW"
                    }
                  ],
                  "extraFees": 150.00,
                  "grandTotal": 2300.00
                }
                """;

        ChatResponse mockChatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage(arabicReceiptJson))));
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);

        MockMultipartFile mockFile = new MockMultipartFile(
                "file",
                "receipt_arabic.pdf",
                "application/pdf",
                "dummy pdf binary content".getBytes()
        );

        ExtractedInvoice invoice = invoiceExtractionService.extractInvoice(mockFile);

        // Verify top-level record fields
        assertThat(invoice).isNotNull();
        assertThat(invoice.invoiceNumber()).isEqualTo("INV-2026-099");
        assertThat(invoice.poReference()).isEqualTo("PO-2026-001");
        assertThat(invoice.vendorName()).isEqualTo("شركة الأهرام للتوريدات الغذائية");
        assertThat(invoice.extraFees()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(invoice.grandTotal()).isEqualByComparingTo(new BigDecimal("2300.00"));

        // Verify extracted items
        assertThat(invoice.items()).isNotNull().hasSize(2);

        ExtractedLineItem item1 = invoice.items().get(0);
        assertThat(item1.vendorItemDescription()).isEqualTo("طماطم بلدي طازجة فاخرة");
        assertThat(item1.quantity()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(item1.unitPrice()).isEqualByComparingTo(new BigDecimal("15.50"));
        assertThat(item1.lineTotal()).isEqualByComparingTo(new BigDecimal("1550.00"));
        assertThat(item1.suggestedSku()).isEqualTo("SKU-TOMATO-RED");

        ExtractedLineItem item2 = invoice.items().get(1);
        assertThat(item2.vendorItemDescription()).isEqualTo("بصل أحمر درجة أولى");
        assertThat(item2.quantity()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(item2.unitPrice()).isEqualByComparingTo(new BigDecimal("12.00"));
        assertThat(item2.lineTotal()).isEqualByComparingTo(new BigDecimal("600.00"));
        assertThat(item2.suggestedSku()).isEqualTo("SKU-ONION-YELLOW");

        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    @DisplayName("Should successfully handle response wrapped in markdown json fences")
    void shouldHandleMarkdownFencedJson() {
        String fencedJson = """
                ```json
                {
                  "invoiceNumber": "INV-2026-100",
                  "poReference": "PO-2026-002",
                  "vendorName": "Al-Amal Agricultural",
                  "items": [
                    {
                      "vendorItemDescription": "بطاطس سبونتا فرز أول",
                      "quantity": 200.0,
                      "unitPrice": 8.75,
                      "lineTotal": 1750.0,
                      "suggestedSku": "SKU-POTATO-SPUNTA"
                    }
                  ],
                  "extraFees": 0.00,
                  "grandTotal": 1750.00
                }
                ```
                """;

        ChatResponse mockChatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage(fencedJson))));
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);

        MockMultipartFile mockFile = new MockMultipartFile(
                "file",
                "invoice.png",
                "image/png",
                "dummy image content".getBytes()
        );

        ExtractedInvoice invoice = invoiceExtractionService.extractInvoice(mockFile);

        assertThat(invoice).isNotNull();
        assertThat(invoice.invoiceNumber()).isEqualTo("INV-2026-100");
        assertThat(invoice.items()).hasSize(1);
        assertThat(invoice.items().get(0).suggestedSku()).isEqualTo("SKU-POTATO-SPUNTA");
        assertThat(invoice.items().get(0).quantity()).isEqualByComparingTo(new BigDecimal("200.0"));
        assertThat(invoice.items().get(0).unitPrice()).isEqualByComparingTo(new BigDecimal("8.75"));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when empty or null file is passed")
    void shouldThrowWhenEmptyFileProvided() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.pdf",
                "application/pdf",
                new byte[0]
        );

        assertThatThrownBy(() -> invoiceExtractionService.extractInvoice(emptyFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot extract invoice from empty or null file.");

        assertThatThrownBy(() -> invoiceExtractionService.extractInvoice(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
