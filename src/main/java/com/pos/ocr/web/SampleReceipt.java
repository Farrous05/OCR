package com.pos.ocr.web;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Renders a synthetic but realistic receipt as pixels — the OCR must genuinely read glyphs. */
public final class SampleReceipt {

    private SampleReceipt() {
    }

    public static BufferedImage render() {
        int w = 520;
        int h = 560;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 24));
        int y = 50;
        for (String line : new String[]{
                "MARKET FRESH",
                "Invoice #R-9001",
                "2026-06-10",
                "",
                "Apple      2   1.50    3.00",
                "Milk       1   2.25    2.25",
                "",
                "SUBTOTAL           5.25",
                "TAX                0.53",
                "TOTAL              5.78",
                "CASH              10.00",
                "CHANGE             4.22",
        }) {
            g.drawString(line, 40, y);
            y += 42;
        }
        g.dispose();
        return img;
    }
}
