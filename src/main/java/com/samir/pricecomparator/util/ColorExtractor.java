package com.samir.pricecomparator.util;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ColorExtractor {

  private static final Map<String, String> COLOR_MAP = new HashMap<>();

  static {
    COLOR_MAP.put("black", "Black");
    COLOR_MAP.put("white", "White");
    COLOR_MAP.put("red", "Red");
    COLOR_MAP.put("blue", "Blue");
    COLOR_MAP.put("green", "Green");
    COLOR_MAP.put("yellow", "Yellow");
    COLOR_MAP.put("purple", "Purple");
    COLOR_MAP.put("pink", "Pink");
    COLOR_MAP.put("orange", "Orange");
    COLOR_MAP.put("gold", "Gold");
    COLOR_MAP.put("silver", "Silver");
    COLOR_MAP.put("gray", "Gray");
    COLOR_MAP.put("grey", "Gray");
    COLOR_MAP.put("brown", "Brown");

    COLOR_MAP.put("qara", "Black");
    COLOR_MAP.put("ağ", "White");
    COLOR_MAP.put("qırmızı", "Red");
    COLOR_MAP.put("mavi", "Blue");
    COLOR_MAP.put("yaşıl", "Green");
    COLOR_MAP.put("sarı", "Yellow");
    COLOR_MAP.put("bənövşəyi", "Purple");
    COLOR_MAP.put("çəhrayı", "Pink");
    COLOR_MAP.put("narıncı", "Orange");
    COLOR_MAP.put("qızılı", "Gold");
    COLOR_MAP.put("gümüşü", "Silver");
    COLOR_MAP.put("boz", "Gray");

    COLOR_MAP.put("черный", "Black");
    COLOR_MAP.put("белый", "White");
    COLOR_MAP.put("красный", "Red");
    COLOR_MAP.put("синий", "Blue");
    COLOR_MAP.put("зеленый", "Green");
    COLOR_MAP.put("желтый", "Yellow");
    COLOR_MAP.put("фиолетовый", "Purple");
    COLOR_MAP.put("розовый", "Pink");
    COLOR_MAP.put("оранжевый", "Orange");
    COLOR_MAP.put("золотой", "Gold");
    COLOR_MAP.put("серебряный", "Silver");
    COLOR_MAP.put("серый", "Gray");

    // Additional common variations
    COLOR_MAP.put("midnight", "Black");
    COLOR_MAP.put("space gray", "Gray");
    COLOR_MAP.put("graphite", "Gray");
    COLOR_MAP.put("starlight", "White");
    COLOR_MAP.put("rose gold", "Gold");
    COLOR_MAP.put("coral", "Orange");
    COLOR_MAP.put("mint", "Green");
    COLOR_MAP.put("lavender", "Purple");
  }

  public String extractColor(String title) {
    if (title == null || title.isBlank()) {
      return null;
    }

    String lowerTitle = title.toLowerCase();

    // Check for each color in the map
    for (Map.Entry<String, String> entry : COLOR_MAP.entrySet()) {
      if (lowerTitle.contains(entry.getKey())) {
        return entry.getValue();
      }
    }

    return null;
  }

  public ColorResult extractColorWithConfidence(String title) {
    String color = extractColor(title);

    if (color == null) {
      return new ColorResult(null, 0.0);
    }

    String lowerTitle = title.toLowerCase();
    double confidence = 0.5; // Base confidence

    if (lowerTitle.contains("color:") || lowerTitle.contains("colour:") ||
        lowerTitle.contains("rəng:") || lowerTitle.contains("цвет:")) {
      confidence += 0.3;
    }

    if (lowerTitle.matches(".*\\([^)]*" + color.toLowerCase() + "[^)]*\\).*")) {
      confidence += 0.2;
    }

    return new ColorResult(color, Math.min(confidence, 1.0));
  }

  public static class ColorResult {
    private final String color;
    private final double confidence;

    public ColorResult(String color, double confidence) {
      this.color = color;
      this.confidence = confidence;
    }

    public String getColor() {
      return color;
    }

    public double getConfidence() {
      return confidence;
    }

    public boolean isConfident() {
      return confidence > 0.7;
    }
  }
}
