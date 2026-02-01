package com.hackathon.codeimpact

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType

object SafetyService {

    fun generateGuards(method: PsiMethod): String {
        val guards = StringBuilder()

        // Iterate over parameters
        for (param in method.parameterList.parameters) {
            val name = param.name
            val type = param.type

            // 1. NUMBER CHECK (Prevent Divide By Zero / Invalid State)
            if (isNumber(type)) {
                guards.append("        if ($name == 0) throw new IllegalArgumentException(\"$name cannot be zero\");\n")
            }
            // 2. OBJECT CHECK (Prevent Null Pointer)
            else if (!isPrimitive(type)) {
                guards.append("        if ($name == null) throw new IllegalArgumentException(\"$name cannot be null\");\n")
            }
        }

        return guards.toString()
    }

    private fun isNumber(type: PsiType): Boolean {
        return type == PsiType.INT || type == PsiType.LONG || type == PsiType.FLOAT || type == PsiType.DOUBLE
    }

    private fun isPrimitive(type: PsiType): Boolean {
        return type is com.intellij.psi.PsiPrimitiveType
    }
}