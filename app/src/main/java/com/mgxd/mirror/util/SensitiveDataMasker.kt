package com.mgxd.mirror.util

import java.util.regex.Pattern

/**
 * 敏感信息脱敏工具。
 * 发送给 AI API 前必须经过脱敏，保护用户隐私。
 */
object SensitiveDataMasker {

    // 手机号：1[3-9]xxxxxxxxx
    private val phonePattern = Pattern.compile("(?<![0-9])1[3-9]\\d{9}(?![0-9])")
    // 身份证：18位
    private val idCardPattern = Pattern.compile("(?<![0-9])[1-9]\\d{16}[0-9Xx](?![0-9])")
    // 银行卡：13-19位连续数字
    private val bankCardPattern = Pattern.compile("(?<![0-9])\\d{13,19}(?![0-9])")
    // 邮箱
    private val emailPattern = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    // 座机
    private val landlinePattern = Pattern.compile("(?<![0-9])0\\d{2,3}-?\\d{7,8}(?![0-9])")

    /** 将文本中的敏感信息替换为 *** */
    fun mask(text: String): String {
        var result = text
        result = phonePattern.matcher(result).replaceAll("***")
        result = idCardPattern.matcher(result).replaceAll("***")
        result = bankCardPattern.matcher(result).replaceAll("***")
        result = emailPattern.matcher(result).replaceAll("***")
        result = landlinePattern.matcher(result).replaceAll("***")
        return result
    }
}