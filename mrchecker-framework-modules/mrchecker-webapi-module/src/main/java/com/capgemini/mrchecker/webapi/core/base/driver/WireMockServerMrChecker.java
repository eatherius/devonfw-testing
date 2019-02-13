package com.capgemini.mrchecker.webapi.core.base.driver;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.Options;

public class WireMockServerMrChecker extends WireMockServer {
	
	public WireMockServerMrChecker(Options options) {
		super(options);
	}
	
	public WireMock getClient() {
		return this.client;
	}
	
}
