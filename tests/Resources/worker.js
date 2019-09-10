/* eslint-env worker */
/* eslint no-unused-expressions: "off" */
/* eslint no-new-func: "off" */
'use strict';
onmessage = function (e) {
	if (e && e.data && typeof e.data.eval === 'string') {
		postMessage(new Function(`return ${e.data.eval}`)());
	}
};
