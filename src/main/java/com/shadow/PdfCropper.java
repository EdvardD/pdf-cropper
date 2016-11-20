package com.shadow;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;

public class PdfCropper {
  public static void main(String[] argv) throws Exception {
    final String inputFilename = argv[0];
    File file = new File(inputFilename);

    PDDocument pdfDocument = PDDocument.load(file);
    PDFRenderer pdfRenderer = new PDFRenderer(pdfDocument);

    PDDocument croppedPdfDocument = new PDDocument();

//    final int[] badPages = { 26, 28, 29, 30, 32 };
    final int[] badPages = { 28 };
    for (int pageIndex = 0; pageIndex < pdfDocument.getNumberOfPages(); pageIndex++) {
//    for (int i = 0; i < badPages.length; i++) {
//      int pageIndex = badPages[i] - 1;
      BufferedImage image = pdfRenderer.renderImage(pageIndex);
      printPage(image);

      int width = image.getWidth();
      int height = image.getHeight();

      int[][] s = getSs(image);
      int[] sColumn = getSColumn(width, height, s);
      int[] sRow = getSRow(width, height, s);
      printColors(sColumn, "column_" + pageIndex + ".txt");

      Bounds xs;
      try {
        xs = getBoundsByString(width, sColumn);
      } catch (Exception e) {
        xs = new Bounds(0, width);
      }

      Bounds ys;
      try {
        ys = getBoundsByString(height, sRow);
      } catch (Exception e) {
        ys = new Bounds(0, height);
      }

/*      int newHeight = xs.len * height / width + 1;
      int diff = newHeight - ys.len;
      if (diff >= 0) {
        ys.minv -= diff / 2;
        ys.len += diff;
      } else {
        int newWidth = ys.len * width / height + 1;
        diff = newWidth - xs.len;
        if (diff >= 0) {
          xs.minv -= diff / 2;
          xs.len += diff;
        } else {
          throw new RuntimeException("Can't scale output image");
        }
      }*/

      System.out.println("Page " + pageIndex + " out of: " + pdfDocument.getNumberOfPages() +
          " minx: " + xs.minv + " miny: " + ys.minv + " width: " + xs.len + " height: " + ys.len);
      image = image.getSubimage(xs.minv, ys.minv, xs.len, ys.len);

      PDPage currentPage = new PDPage(new PDRectangle(xs.len, ys.len));
      croppedPdfDocument.addPage(currentPage);
      PDPageContentStream pageContent = new PDPageContentStream(croppedPdfDocument, currentPage);
      PDImageXObject imageXObject = LosslessFactory.createFromImage(croppedPdfDocument, image);
      pageContent.drawImage(imageXObject, 0, 0);
      pageContent.close();
    }

    pdfDocument.close();

    croppedPdfDocument.save(new File("cropped_" + inputFilename));
    croppedPdfDocument.close();
  }

  static private boolean isWhite(int[] s, int x1, int x2, int percents) {
    int square = (x2 - x1 + 1);
    int blackSquare = getSum(s, x1, x2);
    return blackSquare * 100 < percents * square;
  }

  static private boolean isWhite(int[][] s, int x1, int y1, int x2, int y2, int percents) {
    int square = (x2 - x1 + 1) * (y2 - y1 + 1);
    int blackSquare = getSum(s, x1, y1, x2, y2);
    return blackSquare * 100 < percents * square;
  }

  private static int[] getSRow(int width, int height, int[][] s) {
    final int COLUMN_PERCENTS = 1;

    int[] sRow = new int[height + 1];
    for (int y = 0; y < height; y++) {
      sRow[y + 1] = isWhite(s, 0, y, width - 1, y, COLUMN_PERCENTS) ? 0 : 1;
      sRow[y + 1] += sRow[y];
    }
    return sRow;
  }

  private static int[] getSColumn(int width, int height, int[][] s) {
    final int COLUMN_PERCENTS = 1;

    int[] sColumn = new int[width + 1];
    for (int x = 0; x < width; x++) {
      sColumn[x + 1] = isWhite(s, x, 0, x, height - 1, COLUMN_PERCENTS) ? 0 : 1;
      sColumn[x + 1] += sColumn[x];
    }
    return sColumn;
  }

  private static void printPage(BufferedImage image) throws Exception {
    PrintWriter out = new PrintWriter("page.txt");
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        out.print(image.getRGB(x, y) == -1 ? 0 : 1);
      }
      out.println();
    }
    out.close();
  }

  private static Bounds getBoundsByString(int width, int[] sColumn) {
    for (int columnPercents = 1; columnPercents <= 100; columnPercents++) {
      try {
        Pair xs = getBoundsByStringInternal(width, sColumn, columnPercents);
        xs.minv = Math.max(0, xs.minv - 20);
        xs.maxv = Math.min(width - 1, xs.maxv + 20);
        return new Bounds(xs.minv, xs.maxv - xs.minv + 1);
      } catch (Exception e) {
        // No operations.
      }
    }
    throw new RuntimeException("Can't find xs bounds");
  }

  private static Pair getBoundsByStringInternal(int width, int[] s, int percents) throws Exception {
    Pair xs = new Pair();
    for (int x = width / 2; x >= 0; x--) {
      if (getSum(s, x, x) == 0 && isWhite(s, 0, x, percents)) {
        xs.minv = x;
        break;
      }
    }
    for (int x = width / 2; x < width; x++) {
      if (getSum(s, x, x) == 0 && isWhite(s, x, width - 1, percents)) {
        xs.maxv = x;
        break;
      }
    }
    if (xs.minv == Integer.MAX_VALUE || xs.maxv == Integer.MIN_VALUE) {
      throw new Exception();
    }
    return xs;
  }

  static private class Bounds {
    public int minv, len;
    public Bounds(int minv, int len) {
      this.minv = minv;
      this.len = len;
    }
  };

  static private class Pair {
    public int minv, maxv;
    public Pair() {
      minv = Integer.MAX_VALUE;
      maxv = Integer.MIN_VALUE;
    }
  };

  private static int[][] getSs(BufferedImage image) {
    int[][] s = new int[image.getWidth() + 1][image.getHeight() + 1];
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        s[x + 1][y + 1] = image.getRGB(x, y) == -1 ? 0 : 1;
        s[x + 1][y + 1] += s[x][y + 1];
        s[x + 1][y + 1] += s[x + 1][y];
        s[x + 1][y + 1] -= s[x][y];
      }
    }
    return s;
  }

  static private int getSum(int[] s, int x1, int x2) {
    int ans = 0;
    ans += s[x2 + 1];
    ans -= s[x1];
    return ans;
  }

  static private int getSum(int[][] s, int x1, int y1, int x2, int y2) {
    int ans = 0;
    ans += s[x2 + 1][y2 + 1];
    ans -= s[x1][y2 + 1];
    ans -= s[x2 + 1][y1];
    ans += s[x1][y1];
    return ans;
  }

  private static void printColors(int[] s, String filename) {
    String str = "";
    for (int y = 0; y < s.length - 1; y++) {
      str += getSum(s, y, y);
    }
    try {
      PrintWriter fout = new PrintWriter(filename);
      fout.println(str);
      fout.close();
    } catch (Exception e) {
      System.err.println("Can't write to file: " + filename);
    }
  }
}
