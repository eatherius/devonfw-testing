package com.capgemini.mrchecker.webapi.core.base.driver;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

public class VirtualizedService {
	
	private WireMock		driver;
	private WireMockServer	driverServer;
	private String			host;
	private int				port;
	
	VirtualizedService(WireMock driver, WireMockServer driverServer, String host, int port) {
		this.driver = driver;
		this.driverServer = driverServer;
		this.port = port;
		this.host = host;
		
	}
	
	public WireMock getDriver() {
		return driver;
	}
	
	public WireMockServer getDriverServer() {
		return driverServer;
	}
	
	public int getPort() {
		return port;
	}
	
	public String getHost() {
		return host;
	}
	
	@Override
	public String toString() {
		return "Service for host " + getHost() + ":" + getPort();
	}
};
