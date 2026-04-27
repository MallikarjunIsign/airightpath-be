package com.rightpath.service;

public interface MobileConnectionService {

	void registerDesktop(String token, String sessionId);

	void registerMobile(String token, String sessionId);

	String getDesktopSession(String token);

	String getMobileSession(String token);
	
	void unregister(String token);

}
