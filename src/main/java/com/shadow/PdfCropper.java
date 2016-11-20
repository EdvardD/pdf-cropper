package com.shadow;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;

public class PdfCropper {
  public static void main(String[] argv) throws Exception {
    File file = new File("test.pdf");

    PDDocument pdfDocument = PDDocument.load(file);
    PDFRenderer pdfRenderer = new PDFRenderer(pdfDocument);

    for (int pageIndex = 0; pageIndex < pdfDocument.getNumberOfPages(); pageIndex++) {
      BufferedImage image = pdfRenderer.renderImage(pageIndex);

      int width = image.getWidth();
      int height = image.getHeight();

      int[][] s = getSs(image, width, height);
      int[] sColumn = getSColumn(width, height, s);
//      int[] sRow = getSRow(width, height, s);

      String str = "";
      for (int x = 0; x < width; x++) {
        str += getSum(sColumn, x, x);
      }
      PrintWriter fout = new PrintWriter("out.txt");
      fout.println(str);
      fout.close();

      Pair xs = getXs(width, sColumn);

      int miny = 0, maxy = height - 1;
      System.out.println("minx: " + xs.minv + " miny: " + miny + " maxx: " + xs.maxv + " maxy: " + maxy);
      image = image.getSubimage(xs.minv, miny, xs.maxv - xs.minv + 1, maxy - miny + 1);
      ImageIO.write(image, "PNG", new File("page-" + pageIndex +  ".png"));
    }

    pdfDocument.close();
  }

  private static int[] getSRow(int width, int height, int[][] s) {
    final int COLUMN_PERCENTS = 1;

    int[] sRow = new int[height + 1];
    for (int x = 0; x < height; x++) {
      sRow[x + 1] = isWhite(s, x, 0, x, height - 1, COLUMN_PERCENTS) ? 0 : 1;
      sRow[x + 1] += sRow[x];
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

  private static int[][] getSs(BufferedImage image, int width, int height) {
    int[][] s = new int[width + 1][height + 1];
    for (int x = 0; x < width; x++) {
      for (int y = 0; y < height; y++) {
        s[x + 1][y + 1] = image.getRGB(x, y) == -1 ? 0 : 1;
        s[x + 1][y + 1] += s[x][y + 1];
        s[x + 1][y + 1] += s[x + 1][y];
        s[x + 1][y + 1] -= s[x][y];
      }
    }
    return s;
  }

  private static Pair getXs(int width, int[] sColumn) {
    for (int columnPercents = 1; columnPercents <= 100; columnPercents++) {
      try {
        Pair xs = getXs(width, sColumn, columnPercents);
        xs.minv = Math.max(0, xs.minv - 20);
        xs.maxv = Math.min(width - 1, xs.maxv + 20);
        return xs;
      } catch (Exception e) {
        // No operations.
      }
    }
    throw new RuntimeException("Can't find xs bounds");
  }

  private static Pair getXs(int width, int[] sColumn, int percents) throws Exception {
    Pair xs = new Pair();
    for (int x = width / 2; x >= 0; x--) {
      if (isWhite(sColumn, 0, x, percents)) {
        xs.minv = x;
        break;
      }
    }
    for (int x = width / 2; x < width; x++) {
      if (isWhite(sColumn, x, width - 1, percents)) {
        xs.maxv = x;
        break;
      }
    }
    if (xs.minv == Integer.MAX_VALUE || xs.maxv == Integer.MIN_VALUE) {
      throw new Exception();
    }
    return xs;
  }

  static private class Pair {
    public int minv, maxv;
    public Pair() {
      minv = Integer.MAX_VALUE;
      maxv = Integer.MIN_VALUE;
    }
    public void update(int v) {
      minv = Math.min(minv, v);
      maxv = Math.max(maxv, v);
    }
  };

  static private int getSum(int[] s, int x1, int x2) {
    int ans = 0;
    ans += s[x2 + 1];
    ans -= s[x1];
    return ans;
  }

  static private boolean isWhite(int[] s, int x1, int x2, int percents) {
    int square = (x2 - x1 + 1);
    int blackSquare = getSum(s, x1, x2);
    return blackSquare * 100 < percents * square;
  }

  static private int getSum(int[][] s, int x1, int y1, int x2, int y2) {
    int ans = 0;
    ans += s[x2 + 1][y2 + 1];
    ans -= s[x1][y2 + 1];
    ans -= s[x2 + 1][y1];
    ans += s[x1][y1];
    return ans;
  }

  static private boolean isWhite(int[][] s, int x1, int y1, int x2, int y2, int percents) {
    int square = (x2 - x1 + 1) * (y2 - y1 + 1);
    int blackSquare = getSum(s, x1, y1, x2, y2);
    return blackSquare * 100 < percents * square;
  }
}
