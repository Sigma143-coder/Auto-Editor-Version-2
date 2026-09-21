package com.example

import com.example.data.model.TimestampParser
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun testTimestampParser() {
    // mm-ss format: 0-03 -> 3000ms
    assertEquals(3000L, TimestampParser.parseFilenameToMs("0-03.png"))
    // 0-00 -> 0ms
    assertEquals(0L, TimestampParser.parseFilenameToMs("0-00.png"))
    // 01-15 -> 75000ms
    assertEquals(75000L, TimestampParser.parseFilenameToMs("01-15_scene.jpg"))
    // 0_23_20260920000636 -> 23000ms
    assertEquals(23000L, TimestampParser.parseFilenameToMs("0_23_20260920000636.png"))
    // 1-00 -> 60000ms
    assertEquals(60000L, TimestampParser.parseFilenameToMs("1-00_conclusion.png"))
  }
}

