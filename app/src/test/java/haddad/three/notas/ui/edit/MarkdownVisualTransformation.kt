@file:Suppress("ktlint:standard:function-expression-body")

package haddad.three.notas.ui.edit

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import haddad.three.notas.ui.common.MarkdownStyler

/** Keep the existing parser regression assertions while the field uses OutputTransformation. */
@Suppress("DEPRECATION")
internal class MarkdownVisualTransformation(
    styler: MarkdownStyler,
) {
    private val output = MarkdownOutputTransformation(styler)

    fun filter(text: AnnotatedString): TransformedText {
        val preview = output.preview(text.text)
        return TransformedText(
            preview.text,
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int {
                    return preview.originalToTransformed(offset)
                }

                override fun transformedToOriginal(offset: Int): Int {
                    return preview.transformedToOriginal(offset)
                }
            },
        )
    }
}
