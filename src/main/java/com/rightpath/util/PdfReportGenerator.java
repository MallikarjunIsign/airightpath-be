//package com.rightpath.util;
//
//import java.io.ByteArrayOutputStream;
//
//import org.springframework.stereotype.Component;
//
//import com.lowagie.text.Document;
//import com.lowagie.text.Paragraph;
//import com.lowagie.text.pdf.PdfWriter;
//
//@Component
//public class PdfReportGenerator {
//
//	public byte[] generate(String sessionId, String transcript, String summary) {
//
//		ByteArrayOutputStream out = new ByteArrayOutputStream();
//
//		try {
//			Document document = new Document();
//			PdfWriter.getInstance(document, out);
//			document.open();
//
//			document.add(new Paragraph("AI Interview Report"));
//			document.add(new Paragraph("Session ID: " + sessionId));
//			document.add(new Paragraph(" "));
//
//			document.add(new Paragraph("=== Interview Summary ==="));
//			document.add(new Paragraph(summary));
//			document.add(new Paragraph(" "));
//
//			document.add(new Paragraph("=== Transcript ==="));
//			document.add(new Paragraph(transcript));
//
//			document.close();
//		} catch (Exception e) {
//			throw new RuntimeException("Failed to generate PDF", e);
//		}
//
//		return out.toByteArray();
//	}
//}
