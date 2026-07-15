package com.bluedeck.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics

/** Marks a text field so password managers / Android Autofill can offer credentials. */
fun Modifier.credentialAutofill(contentType: ContentType): Modifier =
    semantics { this.contentType = contentType }
