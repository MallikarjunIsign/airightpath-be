// File: com/rightpath/util/HardcodedDetector.java

package com.rightpath.util;

import java.util.List;

/**
 * Utility class for detecting hardcoded values in code snippets across
 * multiple programming languages like Java, Python, and JavaScript.
 */
public class HardcodedDetector {

    /**
     * Checks if any of the specified values are hardcoded as output statements
     * in the given code snippet.
     *
     * This method scans for common print statements:
     * - Java: System.out.println(value)
     * - Python: print(value)
     * - JavaScript: console.log(value)
     *
     * @param code   The code snippet to scan.
     * @param values A list of values to check against for hardcoding.
     * @return true if a hardcoded value is found in the print statement; false otherwise.
     */
    public static boolean isHardcoded(String code, List<String> values) {
        for (String v : values) {
            if (code.contains("System.out.println(" + v + ")") ||     // Java
                code.contains("print(" + v + ")") ||                   // Python
                code.contains("console.log(" + v + ")")) {             // JavaScript
                return true;
            }
        }
        return false;
    }
}
