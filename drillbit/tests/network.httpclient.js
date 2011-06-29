describe("Ti.Network.HTTPClient tests", {
	
	apiTest: function() {
		valueOf(Ti.Network.createHTTPClient).shouldNotBeNull();
	},
	
	// Test for TIMOB-4513
	secureValidateProperty: function() {
		var xhr = Ti.Network.createHTTPClient();
		valueOf(xhr).shouldBeObject();
		valueOf(xhr.validatesSecureCertificate).shouldBeFalse();
		xhr.validatesSecureCertificate = true;
		valueOf(xhr.validatesSecureCertificate).shouldBeTrue();
		xhr.validatesSecureCertificate = false;
		valueOf(xhr.validatesSecureCertificate).shouldBeFalse();
	},
	
	// https://appcelerator.lighthouseapp.com/projects/32238/tickets/2156-android-invalid-redirect-alert-on-xhr-file-download
	// https://appcelerator.lighthouseapp.com/projects/32238/tickets/1381-android-buffer-large-xhr-downloads
	largeFileWithRedirect: asyncTest({
		start: function() {
			var xhr = Ti.Network.createHTTPClient();
			xhr.setTimeout(60000);
			xhr.onload = this.async(function(e) {
				valueOf(this.responseData.length).shouldBeGreaterThan(0);
			});
			xhr.onerror = this.async(function(e) {
				throw e.error;
			});

			xhr.open('GET','http://www.appcelerator.com/download-win32');
			xhr.send();
		},
		timeout: 60000,
		timeoutError: "Timed out waiting for HTTP download"
	}),

	// https://appcelerator.lighthouseapp.com/projects/32238-titanium-mobile/tickets/1649-android-httpclientsend-with-no-argument-causes-npe
	emptyPOSTSend: asyncTest({
		start: function() {
			var xhr = Ti.Network.createHTTPClient();
			xhr.setTimeout(30000);
			xhr.onload = this.async(function(e) {
				valueOf(1).shouldBe(1);
			});
			xhr.onerror = this.async(function(e) {
				throw e.error;
			});

			xhr.open('POST','http://www.appcelerator.com');
			xhr.send();
		},
		timeout: 30000,
		timeoutError: "Timed out waiting for HTTP onload"
	}),

	//https://appcelerator.lighthouseapp.com/projects/32238/tickets/2339
	responseHeadersBug: asyncTest({
		start: function() {
			var xhr = Ti.Network.createHTTPClient();
			xhr.setTimeout(30000);
			xhr.onload = this.async(function(e) {
				if (Ti.Platform.osname !== 'iphone') {
					// not implemented yet for iOS, see https://appcelerator.lighthouseapp.com/projects/32238-titanium-mobile/tickets/2535
					var allHeaders = xhr.getAllResponseHeaders();
					valueOf(allHeaders.indexOf('Server:')).shouldBeGreaterThanEqual(0);
					var header = xhr.getResponseHeader('Server');
					valueOf(header.length).shouldBeGreaterThan(0);
				}
				else {
					valueOf(1).shouldBe(1);
				}
			});
			xhr.onerror = this.async(function(e) {
				throw e.error;
			});

			xhr.open('GET','http://www.appcelerator.com');
			xhr.send();
		},
		timeout: 30000,
		timeoutError: "Timed out waiting for HTTP onload"
	}),

	     //Confirms that only the selected cookie is deleted
    clearCookiePositiveTest: asyncTest({
        start: function() {
          var second_cookie_fn = this.async(function(e) {
            var second_cookie_string = this.getResponseHeader('Set-Cookie').split(';')[0];
            // New Cookie should be different.
                valueOf(cookie_string == second_cookie_string).shouldBeFalse();
          });

            var xhr = Ti.Network.createHTTPClient();
            var done = false;
            var cookie_string;
            xhr.setTimeout(30000);
            xhr.onload = function(e) {
        cookie_string = this.getResponseHeader('Set-Cookie').split(';')[0];
                xhr.clearCookies("https://my.appcelerator.com");
                xhr.onload=second_cookie_fn;
                xhr.open('GET', 'https://my.appcelerator.com/auth/login');
                xhr.send();
            };
            xhr.onerror = function(e) {
                throw e.error;
            };
            xhr.open('GET','https://my.appcelerator.com/auth/login');
            xhr.send();
        },
        timeout: 30000,
        timeoutError: "Timed out waiting for HTTP onload"
    }),

  //Confirms that only the selected cookie is deleted
  clearCookieUnaffectedCheck: asyncTest({
        start: function() {
          var second_cookie_fn = this.async(function(e) {
            Ti.API.info("Second Load");
            var second_cookie_string = this.getResponseHeader('Set-Cookie').split(';')[0];
            // Cookie should be the same
                valueOf(cookie_string == second_cookie_string).shouldBeTrue();
          });

            var xhr = Ti.Network.createHTTPClient();
            var done = false;
            var cookie_string;
            xhr.setTimeout(30000);
            xhr.onload = function(e) {
        cookie_string = this.getResponseHeader('Set-Cookie').split(';')[0];
                xhr.clearCookies("http://www.microsoft.com");
                xhr.onload=second_cookie_fn;
                xhr.open('GET', 'https://my.appcelerator.com/auth/login');
                xhr.send();
            };
            xhr.onerror = function(e) {
                throw e.error;
            };
            xhr.open('GET','https://my.appcelerator.com/auth/login');
            xhr.send();
        },
        timeout: 30000,
        timeoutError: "Timed out waiting for HTTP onload"
    })

});
