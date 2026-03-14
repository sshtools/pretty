package com.sshtools.pretty;

import java.net.InetSocketAddress;

import picocli.CommandLine.ITypeConverter;

public class InetSocketAddressConverter implements ITypeConverter<InetSocketAddress> {

	@Override
	public InetSocketAddress convert(String value) throws Exception {
		if (value.equals("::")) {
			return new InetSocketAddress("::", 0);
		} else {
			var idx = value.lastIndexOf(':');
			if (idx < 0) {
				return InetSocketAddress.createUnresolved(value, 0);
			} else {
				var host = value.substring(0, idx);
				var port = Integer.parseInt(value.substring(idx + 1));
				return InetSocketAddress.createUnresolved(host, port);
			}
		}
	}

}
