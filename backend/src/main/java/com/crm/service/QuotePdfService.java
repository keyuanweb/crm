package com.crm.service;

import com.crm.dto.quote.QuoteItemResponse;
import com.crm.dto.quote.QuoteResponse;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/** 报价单 PDF 生成（007-product-cpq，FR-P11）：OpenPDF + 打包中文字体。 */
@Service
public class QuotePdfService {

  private static final DecimalFormat AMOUNT = new DecimalFormat("#,##0.00");

  /** 生成报价单 PDF 字节流。 */
  public byte[] generate(QuoteResponse quote) {
    try {
      BaseFont baseFont = BaseFont.createFont(fontPath(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
      Font titleFont = new Font(baseFont, 18, Font.BOLD);
      Font headFont = new Font(baseFont, 11, Font.NORMAL);
      Font cellFont = new Font(baseFont, 10, Font.NORMAL);

      Document document = new Document(PageSize.A4, 40, 40, 40, 40);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      PdfWriter.getInstance(document, out);
      document.open();

      Paragraph title = new Paragraph("报价单 " + quote.getQuoteNo(), titleFont);
      title.setAlignment(Element.ALIGN_CENTER);
      document.add(title);
      document.add(new Paragraph(" ", headFont));

      PdfPTable headTable = new PdfPTable(2);
      headTable.setWidthPercentage(100);
      addCell(headTable, "客户：" + nvl(quote.getCustomerName()), headFont);
      addCell(headTable, "状态：" + statusLabel(quote.getStatus()), headFont);
      addCell(
          headTable,
          "有效期：" + (quote.getValidUntil() == null ? "-" : quote.getValidUntil().toString()),
          headFont);
      addCell(headTable, "创建时间：" + fmtTime(quote), headFont);
      document.add(headTable);
      document.add(new Paragraph(" ", headFont));

      PdfPTable itemsTable = new PdfPTable(6);
      itemsTable.setWidthPercentage(100);
      float[] widths = {4f, 2f, 2.5f, 2f, 2f, 2.5f};
      itemsTable.setWidths(widths);
      addHeader(itemsTable, "产品", cellFont);
      addHeader(itemsTable, "数量", cellFont);
      addHeader(itemsTable, "单价（元）", cellFont);
      addHeader(itemsTable, "折扣", cellFont);
      addHeader(itemsTable, "小计（元）", cellFont);
      addHeader(itemsTable, "备注", cellFont);
      for (QuoteItemResponse item : quote.getItems()) {
        addCell(itemsTable, nvl(item.getProductName()), cellFont);
        addCell(itemsTable, String.valueOf(item.getQuantity()), cellFont);
        addCell(itemsTable, fenToYuan(item.getUnitPrice()), cellFont);
        addCell(
            itemsTable, item.getDiscount() == null ? "100%" : pct(item.getDiscount()), cellFont);
        addCell(itemsTable, fenToYuan(item.getLineTotal()), cellFont);
        addCell(itemsTable, "", cellFont);
      }
      document.add(itemsTable);
      document.add(new Paragraph(" ", headFont));

      Paragraph total = new Paragraph("总 额：" + fenToYuan(quote.getTotalAmount()), titleFont);
      total.setAlignment(Element.ALIGN_RIGHT);
      document.add(total);

      if (quote.getRejectReason() != null) {
        Paragraph reject = new Paragraph("拒绝意见：" + quote.getRejectReason(), headFont);
        document.add(reject);
      }

      document.close();
      return out.toByteArray();
    } catch (Exception ex) {
      throw new IllegalStateException("PDF 生成失败", ex);
    }
  }

  private String fontPath() {
    try {
      // TTC 集合：追加 ",0" 指定第一个字体（WenQuanYiMicroHei）
      return new ClassPathResource("fonts/wqy-microhei.ttc").getFile().getAbsolutePath() + ",0";
    } catch (Exception ex) {
      throw new IllegalStateException("中文字体资源缺失", ex);
    }
  }

  private void addCell(PdfPTable table, String text, Font font) {
    PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
    cell.setPadding(4);
    table.addCell(cell);
  }

  private void addHeader(PdfPTable table, String text, Font font) {
    PdfPCell cell =
        new PdfPCell(new Phrase(text, new Font(font.getBaseFont(), font.getSize(), Font.BOLD)));
    cell.setPadding(4);
    cell.setGrayFill(0.9f);
    table.addCell(cell);
  }

  private String statusLabel(String status) {
    return switch (status == null ? "" : status) {
      case "DRAFT" -> "草稿";
      case "PENDING_APPROVAL" -> "待审批";
      case "APPROVED" -> "已通过";
      case "REJECTED" -> "已拒绝";
      default -> status == null ? "-" : status;
    };
  }

  private String fenToYuan(Long fen) {
    if (fen == null) {
      return "-";
    }
    return AMOUNT.format(fen / 100d);
  }

  private String pct(java.math.BigDecimal discount) {
    return AMOUNT.format(
            discount.multiply(java.math.BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP))
        + "%";
  }

  private String nvl(String s) {
    return s == null || s.isBlank() ? "-" : s;
  }

  private String fmtTime(QuoteResponse quote) {
    return quote.getCreatedAt() == null
        ? "-"
        : quote.getCreatedAt().toString().replace('T', ' ').substring(0, 16);
  }
}
