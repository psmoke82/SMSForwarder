package com.example.smsforwarder

import androidx.compose.ui.text.AnnotatedString
import com.example.smsforwarder.ui.screen.NumberCommaTransformation
import org.junit.Assert.assertEquals
import org.junit.Test

class NumberCommaTransformationTest {

    private val transformation = NumberCommaTransformation()

    @Test
    fun testEmptyString() {
        val result = transformation.filter(AnnotatedString(""))
        assertEquals("", result.text.text)
        assertEquals(0, result.offsetMapping.originalToTransformed(0))
        assertEquals(0, result.offsetMapping.transformedToOriginal(0))
    }

    @Test
    fun testSingleDigit() {
        val result = transformation.filter(AnnotatedString("5"))
        assertEquals("5", result.text.text)
        assertEquals(0, result.offsetMapping.originalToTransformed(0))
        assertEquals(1, result.offsetMapping.originalToTransformed(1))
        assertEquals(0, result.offsetMapping.transformedToOriginal(0))
        assertEquals(1, result.offsetMapping.transformedToOriginal(1))
    }

    @Test
    fun testThreeDigits_NoComma() {
        val result = transformation.filter(AnnotatedString("123"))
        assertEquals("123", result.text.text)
        assertEquals(0, result.offsetMapping.originalToTransformed(0))
        assertEquals(1, result.offsetMapping.originalToTransformed(1))
        assertEquals(2, result.offsetMapping.originalToTransformed(2))
        assertEquals(3, result.offsetMapping.originalToTransformed(3))
    }

    @Test
    fun testFourDigits_OneComma() {
        val result = transformation.filter(AnnotatedString("1234"))
        assertEquals("1,234", result.text.text)
        assertEquals(0, result.offsetMapping.originalToTransformed(0))
        assertEquals(2, result.offsetMapping.originalToTransformed(1))
        assertEquals(3, result.offsetMapping.originalToTransformed(2))
        assertEquals(4, result.offsetMapping.originalToTransformed(3))
        assertEquals(5, result.offsetMapping.originalToTransformed(4))

        assertEquals(0, result.offsetMapping.transformedToOriginal(0))
        assertEquals(1, result.offsetMapping.transformedToOriginal(2))
        assertEquals(4, result.offsetMapping.transformedToOriginal(5))
    }

    @Test
    fun testLargeNumber_MultipleCommas() {
        val result = transformation.filter(AnnotatedString("1234567899"))
        assertEquals("1,234,567,899", result.text.text)
        assertEquals(13, result.offsetMapping.originalToTransformed(10))
        assertEquals(10, result.offsetMapping.transformedToOriginal(13))
    }

    @Test
    fun testContinuousTypingAndBackspaceSimulation() {
        var text = "1"
        assertEquals("1", transformation.filter(AnnotatedString(text)).text.text)

        text += "2"
        assertEquals("12", transformation.filter(AnnotatedString(text)).text.text)

        text += "3"
        assertEquals("123", transformation.filter(AnnotatedString(text)).text.text)

        text += "4"
        assertEquals("1,234", transformation.filter(AnnotatedString(text)).text.text)

        text += "5"
        assertEquals("12,345", transformation.filter(AnnotatedString(text)).text.text)

        // Backspace
        text = text.dropLast(1)
        assertEquals("1,234", transformation.filter(AnnotatedString(text)).text.text)

        text = text.dropLast(1)
        assertEquals("123", transformation.filter(AnnotatedString(text)).text.text)

        text = text.dropLast(1)
        assertEquals("12", transformation.filter(AnnotatedString(text)).text.text)

        text = text.dropLast(1)
        assertEquals("1", transformation.filter(AnnotatedString(text)).text.text)

        text = text.dropLast(1)
        assertEquals("", transformation.filter(AnnotatedString(text)).text.text)

        // Retyping
        text = "9"
        assertEquals("9", transformation.filter(AnnotatedString(text)).text.text)

        text += "8"
        assertEquals("98", transformation.filter(AnnotatedString(text)).text.text)

        text += "7"
        assertEquals("987", transformation.filter(AnnotatedString(text)).text.text)

        text += "6"
        assertEquals("9,876", transformation.filter(AnnotatedString(text)).text.text)
    }
}
