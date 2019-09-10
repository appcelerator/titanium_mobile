package org.appcelerator.kroll;

public interface KrollPromise {

	void resolve(Object value);

	void reject(Object value);
}
