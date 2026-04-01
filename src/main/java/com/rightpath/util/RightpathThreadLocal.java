package com.rightpath.util;

import java.util.HashMap;
import java.util.Map;

public class RightpathThreadLocal {
	public static ThreadLocal<Map<String, String>> edgeThreadLocalholder = ThreadLocal.withInitial(HashMap::new);
}
