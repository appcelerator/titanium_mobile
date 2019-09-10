'use strict';

exports.Worker = function () {
	if (arguments.length === 0) {
		return Titanium.Worker.createWorker();
	}
	if (arguments.length === 1) {
		return Titanium.Worker.createWorker(arguments[0]);
	}
	if (arguments.length === 2) {
		return Titanium.Worker.createWorker(arguments[0], arguments[1]);
	}
};
