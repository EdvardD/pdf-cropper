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

    final int[] badPages = { 54, 85, 103, 150, 147 };
//    final int[] badPages = { 103 };
//    for (int pageIndex = 0; pageIndex < pdfDocument.getNumberOfPages(); pageIndex++) {
    for (int i = 0; i < badPages.length; i++) {
      int pageIndex = badPages[i] - 1;
      BufferedImage image = pdfRenderer.renderImage(pageIndex);
      printPage(image);

      int width = image.getWidth();
      int height = image.getHeight();

      int[][] s = getSs(image);
      int[] columns = getSColumn(width, height, s);
      int[] rows = getSRow(width, height, s);

/*      normalize(columns, "column_" + pageIndex + ".txt");
      normalize(rows, "row_" + pageIndex + ".txt");*/

      Bounds xs;
      try {
        xs = getBounds(columns);
      } catch (Exception e) {
        xs = new Bounds(0, width);
      }

      Bounds ys;
      try {
        ys = getBounds(rows);
      } catch (Exception e) {
        ys = new Bounds(0, height);
      }

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

/*  private static Bounds getBounds(int[] a) {
    int cnt = 0;
    Bounds result = new Bounds(0, a.length);
    for (int i = 0, j = 0; i < a.length; i = j) {
      while (j < a.length && a[j] == a[i]) j++;

      if (a[i] == 1) {
        cnt++;
        int minv = i, maxv = j - 1;
        minv = Math.max(0, minv - 20);
        maxv = Math.min(a.length - 1, maxv + 20);
        result = new Bounds(minv, maxv - minv + 1);
      }
    }
    if (cnt != 1) {
      throw new RuntimeException("Can't find xs bounds");
    }
    return result;
  }*/

  private static void normalize(int[] a, String filename) {
    final int n = a.length;
    PrintWriter fout;
    try {
      fout = new PrintWriter(filename);
    } catch (Exception e) {
      System.err.println("Can't write to file: " + filename);
      return;
    }
    fout.println(getString(a));
    while (true) {
      int[] cnt = new int[n];
      for (int i = 0, j = 0; i < n; i = j) {
        while (j < n && a[j] == a[i]) {
          j++;
        }
        for (int k = i; k < j; k++) {
          cnt[k] = j - i;
        }
      }

      int[] na = new int[n];
      for (int i = 0, j = 0; i < n; i = j) {
        while (j < n && a[j] == a[i]) {
          j++;
        }

        int cur = 0;
        if (i > 0) cur += cnt[i - 1];
        if (j < n) cur += cnt[j];

        boolean invert = j - i < cur;
        if (a[i] == 0 && i == 0 || j == n) {
          invert = false;
        }

        for (int k = i; k < j; k++) {
          na[k] = a[k];
          if (invert) {
            na[k] ^= 1;
          }
        }
      }

      boolean finished = true;
      for (int i = 0; i < n; i++) {
        if (na[i] != a[i]) {
          finished = false;
          a[i] = na[i];
        }
      }
      if (finished) {
        break;
      }
      fout.println(getString(a));
    }
    try {
      fout.close();
    } catch (Exception e) {
      System.err.println("Can't write to file: " + filename);
    }
  }

  static private Bounds getBounds(int[] a) {
    int n = a.length;
    int[] s = new int[n + 1];
    s[0] = 0;
    for (int i = 0; i < n; i++) {
      s[i + 1] = s[i] + (a[i] == 1 ? +1 : -1);
    }

    Bounds bounds = new Bounds(0, n);
    int maxSum = s[n];

    int minVal = 0;
    int minPos = 0;
    for (int i = 1; i <= n; i++) {
      if (minVal > s[i]) {
        minVal = s[i];
        minPos = i;
      }
      if (s[i] - minVal > maxSum) {
        maxSum = s[i] - minVal;
        bounds = new Bounds(minPos, i - minPos);
      }
    }
    return bounds;
  }

  static private boolean isWhite(int[][] s, int x1, int y1, int x2, int y2, int percents) {
    int square = (x2 - x1 + 1) * (y2 - y1 + 1);
    int blackSquare = getSum(s, x1, y1, x2, y2);
    return blackSquare * 100 < percents * square;
  }

  private static int[] getSRow(int width, int height, int[][] s) {
    final int COLUMN_PERCENTS = 1;
    int[] sRow = new int[height];
    for (int y = 0; y < height; y++) {
      sRow[y] = isWhite(s, 0, y, width - 1, y, COLUMN_PERCENTS) ? 0 : 1;
    }
    return sRow;
  }

  private static int[] getSColumn(int width, int height, int[][] s) {
    final int COLUMN_PERCENTS = 1;
    int[] sColumn = new int[width];
    for (int x = 0; x < width; x++) {
      sColumn[x] = isWhite(s, x, 0, x, height - 1, COLUMN_PERCENTS) ? 0 : 1;
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

  static private class Bounds {
    public int minv, len;
    public Bounds(int minv, int len) {
      this.minv = minv;
      this.len = len;
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

  static private int getSum(int[][] s, int x1, int y1, int x2, int y2) {
    int ans = 0;
    ans += s[x2 + 1][y2 + 1];
    ans -= s[x1][y2 + 1];
    ans -= s[x2 + 1][y1];
    ans += s[x1][y1];
    return ans;
  }

  private static String getString(int[] a) {
    String str = "";
    for (int y = 0; y < a.length; y++) {
      str += a[y];
    }
    return str;
  }
}
