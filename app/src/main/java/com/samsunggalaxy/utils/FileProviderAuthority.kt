package com.samsunggalaxy.utils

/**
 * Single source of truth for the FileProvider authority declared in AndroidManifest.xml —
 * previously duplicated as a private constant in both CsvExporter and ShareProgressCardExporter,
 * risking the two silently drifting apart on a future rename.
 */
const val FILE_PROVIDER_AUTHORITY = "com.samsunggalaxy.MainActivity.provider"
