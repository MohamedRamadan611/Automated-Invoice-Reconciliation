"use client";

import React, { useState, useRef } from "react";
import {
  FileText,
  ZoomIn,
  ZoomOut,
  Maximize2,
  ExternalLink,
  Download,
  MoveHorizontal,
  RotateCcw,
  Minimize2,
} from "lucide-react";

interface DocumentViewerProps {
  fileUri: string;
  fileName?: string;
  isImage?: boolean;
}

export default function DocumentViewer({
  fileUri,
  fileName = "Invoice Document",
  isImage = false,
}: DocumentViewerProps) {
  const [zoomLevel, setZoomLevel] = useState<number>(100);
  const [fitMode, setFitMode] = useState<"fit-width" | "custom">("fit-width");
  const [isFullscreen, setIsFullscreen] = useState<boolean>(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const handleZoomIn = () => {
    setFitMode("custom");
    setZoomLevel((prev) => Math.min(prev + 15, 250));
  };

  const handleZoomOut = () => {
    setFitMode("custom");
    setZoomLevel((prev) => Math.max(prev - 15, 50));
  };

  const handleResetZoom = () => {
    setFitMode("custom");
    setZoomLevel(100);
  };

  const handleFitWidth = () => {
    setFitMode("fit-width");
    setZoomLevel(100);
  };

  const toggleFullscreen = () => {
    if (!containerRef.current) return;
    if (!document.fullscreenElement) {
      containerRef.current.requestFullscreen().catch((err) => {
        console.error("Fullscreen request failed:", err);
      });
      setIsFullscreen(true);
    } else {
      document.exitFullscreen().catch((err) => {
        console.error("Exit fullscreen failed:", err);
      });
      setIsFullscreen(false);
    }
  };

  // URL parameters for modern PDF viewers:
  // #view=FitH forces horizontal fit to the container width.
  // toolbar=1 and navpanes=0 ensure standard reading navigation.
  const pdfViewUri = `${fileUri}#view=FitH&toolbar=1&navpanes=0`;

  return (
    <div
      ref={containerRef}
      className={`relative flex flex-col h-full w-full min-h-[700px] bg-slate-900 border-r border-slate-200 overflow-hidden ${
        isFullscreen ? "p-0" : ""
      }`}
    >
      {/* Document Viewer Toolbar */}
      <div className="h-11 bg-slate-900 border-b border-slate-800 px-4 flex items-center justify-between text-xs text-slate-300 shrink-0 z-10">
        <div className="flex items-center gap-2">
          <FileText className="w-4 h-4 text-indigo-400" />
          <span className="font-medium text-slate-200 truncate max-w-[200px]" title={fileName}>
            {fileName}
          </span>
          <span className="text-[10px] text-slate-400 font-mono bg-slate-800 px-1.5 py-0.5 rounded">
            {fitMode === "fit-width" ? "Fit Width" : `${zoomLevel}%`}
          </span>
        </div>

        {/* Toolbar Controls */}
        <div className="flex items-center gap-1">
          {/* Fit Width Button */}
          <button
            onClick={handleFitWidth}
            className={`px-2 py-1 rounded-md text-[11px] font-semibold transition-colors flex items-center gap-1 ${
              fitMode === "fit-width"
                ? "bg-indigo-600 text-white"
                : "bg-slate-800 text-slate-300 hover:text-white hover:bg-slate-700"
            }`}
            title="Force Horizontal Fit (Fit Width)"
          >
            <MoveHorizontal className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">Fit Width</span>
          </button>

          {/* Zoom Out */}
          <button
            onClick={handleZoomOut}
            className="p-1.5 hover:bg-slate-800 rounded-md text-slate-400 hover:text-white transition-colors"
            title="Zoom Out"
          >
            <ZoomOut className="w-3.5 h-3.5" />
          </button>

          {/* 100% Reset */}
          <button
            onClick={handleResetZoom}
            className={`px-2 py-1 rounded-md text-[11px] font-mono transition-colors ${
              fitMode === "custom" && zoomLevel === 100
                ? "bg-slate-700 text-white"
                : "text-slate-400 hover:bg-slate-800 hover:text-white"
            }`}
            title="Reset Zoom to 100%"
          >
            100%
          </button>

          {/* Zoom In */}
          <button
            onClick={handleZoomIn}
            className="p-1.5 hover:bg-slate-800 rounded-md text-slate-400 hover:text-white transition-colors"
            title="Zoom In"
          >
            <ZoomIn className="w-3.5 h-3.5" />
          </button>

          <div className="h-3 w-px bg-slate-700 mx-1" />

          {/* Fullscreen Toggle */}
          <button
            onClick={toggleFullscreen}
            className="p-1.5 hover:bg-slate-800 rounded-md text-slate-400 hover:text-white transition-colors"
            title={isFullscreen ? "Exit Fullscreen" : "Fullscreen Preview"}
          >
            {isFullscreen ? <Minimize2 className="w-3.5 h-3.5" /> : <Maximize2 className="w-3.5 h-3.5" />}
          </button>

          {/* New Tab */}
          <a
            href={fileUri}
            target="_blank"
            rel="noreferrer"
            className="p-1.5 hover:bg-slate-800 rounded-md text-slate-400 hover:text-white transition-colors flex items-center gap-1"
            title="Open original document in new browser tab"
          >
            <ExternalLink className="w-3.5 h-3.5" />
            <span className="text-[11px] hidden sm:inline">New Tab</span>
          </a>

          {/* Download */}
          <a
            href={fileUri}
            download
            className="p-1.5 hover:bg-slate-800 rounded-md text-slate-400 hover:text-white transition-colors flex items-center gap-1"
            title="Download document"
          >
            <Download className="w-3.5 h-3.5" />
          </a>
        </div>
      </div>

      {/* Main Viewport Container */}
      <div className="flex-1 min-h-0 w-full h-full overflow-auto bg-slate-950 flex items-center justify-center relative">
        {isImage ? (
          <div
            style={{
              transform: `scale(${zoomLevel / 100})`,
              transformOrigin: "top center",
              transition: "transform 0.15s ease-out",
            }}
            className="w-full h-full flex items-center justify-center p-4"
          >
            <img
              src={fileUri}
              alt={fileName}
              className="max-w-full max-h-full object-contain rounded-md shadow-2xl border border-slate-800 bg-white"
            />
          </div>
        ) : (
          <div className="w-full h-full min-h-[700px] flex-1">
            <object
              data={pdfViewUri}
              type="application/pdf"
              className="w-full h-full min-h-[700px] border-0 rounded-none bg-white"
            >
              <iframe
                src={pdfViewUri}
                title={fileName}
                className="w-full h-full min-h-[700px] border-0 rounded-none bg-white"
              >
                <div className="p-8 text-center text-slate-400 bg-slate-900 rounded-xl flex flex-col items-center justify-center space-y-3 m-6">
                  <FileText className="w-10 h-10 text-slate-500" />
                  <p className="text-sm font-medium text-slate-300">
                    Unable to render embedded PDF preview in this browser viewport.
                  </p>
                  <a
                    href={fileUri}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold shadow-sm transition-colors"
                  >
                    <ExternalLink className="w-3.5 h-3.5" /> Open Document in New Tab
                  </a>
                </div>
              </iframe>
            </object>
          </div>
        )}
      </div>
    </div>
  );
}
