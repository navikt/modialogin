package no.nav.modialogin.common.features

private val maskingPattern = "(^|\\W)\\d{11}(?=$|\\W)".toRegex()
fun String.maskSensitiveInfo() = this.replace(maskingPattern, "$1***********")
