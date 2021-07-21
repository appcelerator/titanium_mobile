/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2020-Present by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
/* eslint-env mocha */
/* eslint no-unused-expressions: "off" */
/* eslint no-undef: "off" */
/* eslint mocha/no-identical-title: "off" */
'use strict';

const should = require('./utilities/assertions');

describe('Titanium.Media', () => {

	describe('properties', () => {
		describe('.apiName', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.readOnlyProperty('apiName').which.is.a.String();
			});

			it('equals Ti.Media', () => {
				should(Ti.Media.apiName).eql('Ti.Media');
			});
		});

		describe.ios('.appMusicPlayer', () => {
			it('is a Titanium.Media.MusicPlayer', () => {
				should(Ti.Media).have.a.readOnlyProperty('appMusicPlayer').which.is.an.Object();
				should(Ti.Media.appMusicPlayer).have.a.readOnlyProperty('apiName').which.eql('Ti.Media.MusicPlayer');
			});
		});

		describe.ios('.audioPlaying', () => {
			it('is a Boolean', () => {
				should(Ti.Media).have.a.readOnlyProperty('audioPlaying').which.is.a.Boolean();
			});
		});

		describe.ios('.audioSessionCategory', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.property('audioSessionCategory').which.is.a.String();
			});

			it('is one of Ti.Media.AUDIO_SESSION_CATEGORY_*', () => {
				should([
					Ti.Media.AUDIO_SESSION_CATEGORY_AMBIENT,
					Ti.Media.AUDIO_SESSION_CATEGORY_PLAYBACK,
					Ti.Media.AUDIO_SESSION_CATEGORY_PLAY_AND_RECORD,
					Ti.Media.AUDIO_SESSION_CATEGORY_RECORD,
					Ti.Media.AUDIO_SESSION_CATEGORY_SOLO_AMBIENT,
				]).containEql(Ti.Media.audioSessionCategory);
			});
		});

		describe.ios('.availableCameraMediaTypes', () => {
			it('is an Array', () => {
				// TODO: verify it's an array of Strings
				should(Ti.Media).have.a.property('availableCameraMediaTypes').which.is.an.Array();
			});

			// TODO: Verify the members of the array are one of these constants!
			// it('is one of Ti.Media.MEDIA_TYPE_*', () => {
			// 	should([
			// 		Ti.Media.MEDIA_TYPE_PHOTO,
			// 		Ti.Media.MEDIA_TYPE_LIVEPHOTO,
			// 		Ti.Media.MEDIA_TYPE_VIDEO,
			// 	]).containEql(Ti.Media.availableCameraMediaTypes);
			// });
		});

		describe('.availableCameras', () => {
			it('is an Array', () => {
				// TODO: Verify it's an array of numbers
				should(Ti.Media).have.a.readOnlyProperty('availableCameras').which.is.an.Array();
				should(Ti.Media.availableCameras).be.an.Array(); // necessary for ios devices due to way we sniff api usage to turn on/off defines
			});

			// TODO: Verify the members of the array are one of these constants!
			// it('is one of Ti.Media.CAMERA_*', () => {
			// 	should([
			// 		Ti.Media.CAMERA_FRONT,
			// 		Ti.Media.CAMERA_REAR,
			// 	]).containEql(Ti.Media.availableCameras);
			// });
		});

		describe.ios('.availablePhotoGalleryMediaTypes', () => {
			it('is am Array', () => {
				// TODO: verify it's an array of Strings
				should(Ti.Media).have.a.property('availablePhotoGalleryMediaTypes').which.is.an.Array();
			});

			// TODO: Verify the members of the array are one of these constants!
			// it('is one of Ti.Media.MEDIA_TYPE_*', () => {
			// 	should([
			// 		Ti.Media.MEDIA_TYPE_PHOTO,
			// 		Ti.Media.MEDIA_TYPE_LIVEPHOTO,
			// 		Ti.Media.MEDIA_TYPE_VIDEO,
			// 	]).containEql(Ti.Media.availablePhotoGalleryMediaTypes);
			// });
		});

		describe.ios('.availablePhotoMediaTypes', () => {
			it('is an Array', () => {
				// TODO: verify it's an array of Strings
				should(Ti.Media).have.a.property('availablePhotoMediaTypes').which.is.an.Array();
			});

			// TODO: Verify the members of the array are one of these constants!
			// it('is one of Ti.Media.MEDIA_TYPE_*', () => {
			// 	should([
			// 		Ti.Media.MEDIA_TYPE_PHOTO,
			// 		Ti.Media.MEDIA_TYPE_LIVEPHOTO,
			// 		Ti.Media.MEDIA_TYPE_VIDEO,
			// 	]).containEql(Ti.Media.availablePhotoMediaTypes);
			// });
		});

		describe.ios('.averageMicrophonePower', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.property('averageMicrophonePower').which.is.a.Number();
			});
		});

		describe.ios('.cameraAuthorization', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.readOnlyProperty('cameraAuthorization').which.is.a.Number();
			});

			it('is one of Ti.Media.CAMERA_AUTHORIZATION_*', () => {
				should([
					Ti.Media.CAMERA_AUTHORIZATION_AUTHORIZED,
					Ti.Media.CAMERA_AUTHORIZATION_DENIED,
					Ti.Media.CAMERA_AUTHORIZATION_RESTRICTED,
					Ti.Media.CAMERA_AUTHORIZATION_UNKNOWN,
				]).containEql(Ti.Media.cameraAuthorization);
			});
		});

		describe('.cameraFlashMode', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.property('cameraFlashMode').which.is.a.Number();
			});

			it('defaults to Ti.Media.CAMERA_FLASH_AUTO on iOS, Ti.Media.CAMERA_FLASH_OFF on Android', () => {
				if (OS_ANDROID) {
					should(Ti.Media.cameraFlashMode).eql(Titanium.Media.CAMERA_FLASH_OFF);
				} else {
					should(Ti.Media.cameraFlashMode).eql(Titanium.Media.CAMERA_FLASH_AUTO);
				}
			});

			it('is one of Ti.Media.CAMERA_FLASH_*', () => {
				should([
					Ti.Media.CAMERA_FLASH_AUTO,
					Ti.Media.CAMERA_FLASH_OFF,
					Ti.Media.CAMERA_FLASH_ON,
				]).containEql(Ti.Media.cameraFlashMode);
			});
		});

		describe('.canRecord', () => {
			it('is a Boolean', () => {
				should(Ti.Media).have.a.readOnlyProperty('canRecord').which.is.a.Boolean();
			});
		});

		describe.ios('.currentRoute', () => {
			it('is a RouteDescription', () => {
				should(Ti.Media).have.a.readOnlyProperty('currentRoute').which.is.an.Object();
				should(Ti.Media.currentRoute).have.a.property('inputs').which.is.an.Array();
				should(Ti.Media.currentRoute).have.a.property('outputs').which.is.an.Array();
			});
		});

		describe('.isCameraSupported', () => {
			it('is a Boolean', () => {
				should(Ti.Media).have.a.readOnlyProperty('isCameraSupported').which.is.a.Boolean();
				should(Ti.Media.isCameraSupported).be.a.Boolean(); // necessary for ios devices due to way we sniff api usage to turn on/off defines
			});
		});

		describe.ios('.peakMicrophonePower', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.readOnlyProperty('peakMicrophonePower').which.is.a.Number();
			});
		});

		describe('.QUALITY_640x480', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.readOnlyProperty('QUALITY_640x480').which.is.a.Number();
			});
		});

		describe('.QUALITY_IFRAME_1280x720', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.readOnlyProperty('QUALITY_IFRAME_1280x720').which.is.a.Number();
			});
		});

		describe.ios('.QUALITY_IFRAME_960x540', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.readOnlyProperty('QUALITY_IFRAME_960x540').which.is.a.Number();
			});
		});

		describe.ios('.systemMusicPlayer', () => {
			it('is a Titanium.Media.MusicPlayer', () => {
				should(Ti.Media).have.a.readOnlyProperty('systemMusicPlayer').which.is.an.Object();
				should(Ti.Media.systemMusicPlayer).have.a.readOnlyProperty('apiName').which.eql('Ti.Media.MusicPlayer');
			});
		});

		describe.ios('.volume', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.readOnlyProperty('volume').which.is.a.Number();
			});
		});
	});

	describe('methods', () => {
		describe.ios('#beep', () => {
			it('is a Function', () => {
				should(Ti.Media.beep).be.a.Function();
			});
		});

		describe('#hasAudioRecorderPermissions', () => {
			it('is a Function', () => {
				should(Ti.Media.hasAudioRecorderPermissions).be.a.Function();
			});
		});

		describe('#hasCameraPermissions', () => {
			it('is a Function', () => {
				should(Ti.Media.hasCameraPermissions).be.a.Function();
			});
		});

		describe.ios('#hasMusicLibraryPermissions', () => {
			it('is a Function', () => {
				should(Ti.Media.hasMusicLibraryPermissions).be.a.Function();
			});
		});

		describe('#hasPhotoGalleryPermissions', () => {
			it('is a Function', () => {
				should(Ti.Media.hasPhotoGalleryPermissions).be.a.Function();
			});
		});

		describe('#hideCamera', () => {
			it('is a Function', () => {
				should(Ti.Media.hideCamera).be.a.Function();
			});
		});

		describe.ios('#hideMusicLibrary', () => {
			it('is a Function', () => {
				should(Ti.Media.hideMusicLibrary).be.a.Function();
			});
		});

		describe.ios('#isMediaTypeSupported', () => {
			it('is a Function', () => {
				should(Ti.Media.isMediaTypeSupported).be.a.Function();
			});
		});

		describe.ios('#openMusicLibrary', () => {
			it('is a Function', () => {
				should(Ti.Media.openMusicLibrary).be.a.Function();
			});
		});

		describe('#openPhotoGallery', () => {
			it('is a Function', () => {
				should(Ti.Media.openPhotoGallery).be.a.Function();
			});
		});

		describe.android('#previewImage', () => {
			it('is a Function', () => {
				should(Ti.Media.previewImage).be.a.Function();
			});

			// FIXME: java.lang.ClassCastException: byte[] cannot be cast to org.appcelerator.titanium.io.TiBaseFile
			// This assumes the TiBlob is from a file, but in this case it's not.
			// MediaModule.java needs to be updated to write to a temp file in this case,
			// like we do for EmailDialogProxy
			it.androidBroken('from screenshot', finish => {
				Ti.Media.takeScreenshot(image => {
					if (image && image.media) {
						Ti.Media.previewImage({
							success: () => finish(),
							error: finish,
							image
						});
					} else {
						finish(new Error('failed to obtain screenshot'));
					}
				});
			});

			// FIXME: Fails to write to file on CI machine
			// Presumably it's because we need to ask for storage permissions, which we can't do in a headless way
			// it.android('preview image read/write external storage', function (finish) {
			it.androidBroken('preview image read/write external storage', finish => {
				Ti.Media.takeScreenshot(image => {
					if (image && image.media) {
						const tmp = Ti.Filesystem.getFile(Ti.Filesystem.externalStorageDirectory, 'temp.png');

						// write to external storage
						tmp.write(image.media);

						// preview image from external storage
						Ti.Media.previewImage({
							success: () => finish(),
							error: finish,
							image: tmp.read()
						});
					} else {
						finish(new Error('failed to obtain screenshot'));
					}
				});
			});
		});

		describe.ios('#queryMusicLibrary', () => {
			it('is a Function', () => {
				should(Ti.Media.queryMusicLibrary).be.a.Function();
			});
		});

		describe('#requestAudioRecorderPermissions', () => {
			it('is a Function', () => {
				should(Ti.Media.requestAudioRecorderPermissions).be.a.Function();
			});
		});

		describe('#requestCameraPermissions', () => {
			it('is a Function', () => {
				should(Ti.Media.requestCameraPermissions).be.a.Function();
			});
		});

		describe.ios('#requestMusicLibraryPermissions', () => {
			it('is a Function', () => {
				should(Ti.Media.requestMusicLibraryPermissions).be.a.Function();
			});
		});

		describe('#requestPhotoGalleryPermissions', () => {
			it('is a Function', () => {
				should(Ti.Media.requestPhotoGalleryPermissions).be.a.Function();
			});
		});

		describe('#saveToPhotoGallery', () => {
			it('is a Function', () => {
				should(Ti.Media.saveToPhotoGallery).be.a.Function();
			});

			it('blob', finish => {
				if (!Ti.Media.hasPhotoGalleryPermissions()) {
					return finish();
				}
				const blob = Ti.Filesystem.getFile(Ti.Filesystem.resourcesDirectory, 'Logo.png').read();
				Ti.Media.saveToPhotoGallery(blob, {
					success: () => finish(),
					error: (e) => finish(new Error(e.message))
				});
			});

			it('file - resourcesDirectory', finish => {
				if (!Ti.Media.hasPhotoGalleryPermissions()) {
					return finish();
				}
				const file = Ti.Filesystem.getFile(Ti.Filesystem.resourcesDirectory, 'Logo.png');
				Ti.Media.saveToPhotoGallery(file, {
					success: () => finish(),
					error: (e) => finish(new Error(e.message))
				});
			});

			it('file - applicationDataDirectory', finish => {
				if (!Ti.Media.hasPhotoGalleryPermissions()) {
					return finish();
				}
				const internalFile = Ti.Filesystem.getFile(Ti.Filesystem.resourcesDirectory, 'Logo.png');
				const externalFile = Ti.Filesystem.getFile(Ti.Filesystem.applicationDataDirectory, 'SaveToPhotoGallery.png');
				if (externalFile.exists()) {
					externalFile.deleteFile();
				}
				internalFile.copy(externalFile.nativePath);
				Ti.Media.saveToPhotoGallery(externalFile, {
					success: () => finish(),
					error: (e) => finish(new Error(e.message))
				});
			});
		});

		describe.ios('#setOverrideAudioRoute', () => {
			it('is a Function', () => {
				should(Ti.Media.setOverrideAudioRoute).be.a.Function();
			});
		});

		describe('#showCamera', () => {
			it('is a Function', () => {
				should(Ti.Media.showCamera).be.a.Function();
			});
		});

		describe.ios('#startMicrophoneMonitor', () => {
			it('is a Function', () => {
				should(Ti.Media.startMicrophoneMonitor).be.a.Function();
			});
		});

		describe('#startVideoCapture', () => {
			it('is a Function', () => {
				should(Ti.Media.startVideoCapture).be.a.Function();
			});
		});

		describe.ios('#stopMicrophoneMonitor', () => {
			it('is a Function', () => {
				should(Ti.Media.stopMicrophoneMonitor).be.a.Function();
			});
		});

		describe('#stopVideoCapture', () => {
			it('is a Function', () => {
				should(Ti.Media.stopVideoCapture).be.a.Function();
			});
		});

		describe('#switchCamera', () => {
			it('is a Function', () => {
				should(Ti.Media.switchCamera).be.a.Function();
			});
		});

		describe('#takePicture', () => {
			it('is a Function', () => {
				should(Ti.Media.takePicture).be.a.Function();
			});
		});

		describe('#takeScreenshot', () => {
			it('is a Function', () => {
				should(Ti.Media.takeScreenshot).be.a.Function();
			});

			it('is async and returns image as Ti.Blob on \'media\' property', finish => {
				Ti.Media.takeScreenshot(image => {
					if (image && image.media) {
						try {
							should(image.media).have.a.readOnlyProperty('apiName').which.eql('Ti.Blob');
						} catch (err) {
							return finish(err);
						}
						finish();
					} else {
						finish(new Error('failed to obtain screenshot'));
					}
				});
			});
		});

		describe('#vibrate', () => {
			it('is a Function', () => {
				should(Ti.Media.vibrate).be.a.Function();
			});
		});
	});

	describe('constants', () => {
		describe.ios('.AUDIO_FILEFORMAT_3GP2', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('AUDIO_FILEFORMAT_3GP2').which.is.a.Number();
			});
		});

		describe.ios('.AUDIO_FILEFORMAT_3GPP', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('AUDIO_FILEFORMAT_3GPP').which.is.a.Number();
			});
		});

		describe.ios('.AUDIO_FILEFORMAT_AIFF', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('AUDIO_FILEFORMAT_AIFF').which.is.a.Number();
			});
		});

		describe.ios('.AUDIO_FILEFORMAT_AMR', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('AUDIO_FILEFORMAT_AMR').which.is.a.Number();
			});
		});

		describe.ios('.AUDIO_FILEFORMAT_CAF', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('AUDIO_FILEFORMAT_CAF').which.is.a.Number();
			});
		});

		describe.ios('.AUDIO_FILEFORMAT_MP3', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('AUDIO_FILEFORMAT_MP3').which.is.a.Number();
			});
		});

		describe.ios('.AUDIO_FILEFORMAT_MP4', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('AUDIO_FILEFORMAT_MP4').which.is.a.Number();
			});
		});

		describe.ios('.AUDIO_FILEFORMAT_MP4A', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('AUDIO_FILEFORMAT_MP4A').which.is.a.Number();
			});
		});

		describe.ios('.AUDIO_FILEFORMAT_WAVE', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('AUDIO_FILEFORMAT_WAVE').which.is.a.Number();
			});
		});

		describe.ios('.AUDIO_FORMAT_AAC', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('AUDIO_FORMAT_AAC').which.is.a.Number();
			});
		});

		describe.ios('.AUDIO_FORMAT_ALAW', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('AUDIO_FORMAT_ALAW').which.is.a.Number();
			});
		});

		describe.ios('.AUDIO_FORMAT_APPLE_LOSSLESS', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('AUDIO_FORMAT_APPLE_LOSSLESS').which.is.a.Number();
			});
		});

		describe.ios('.AUDIO_FORMAT_ILBC', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('AUDIO_FORMAT_ILBC').which.is.a.Number();
			});
		});

		describe.ios('.AUDIO_FORMAT_IMA4', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('AUDIO_FORMAT_IMA4').which.is.a.Number();
			});
		});

		describe.ios('.AUDIO_FORMAT_LINEAR_PCM', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('AUDIO_FORMAT_LINEAR_PCM').which.is.a.Number();
			});
		});

		describe.ios('.AUDIO_FORMAT_ULAW', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('AUDIO_FORMAT_ULAW').which.is.a.Number();
			});
		});

		describe.ios('.AUDIO_SESSION_CATEGORY_AMBIENT', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('AUDIO_SESSION_CATEGORY_AMBIENT').which.is.a.String();
			});
		});

		describe.ios('.AUDIO_SESSION_CATEGORY_PLAY_AND_RECORD', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('AUDIO_SESSION_CATEGORY_PLAY_AND_RECORD').which.is.a.String();
			});
		});

		describe.ios('.AUDIO_SESSION_CATEGORY_PLAYBACK', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('AUDIO_SESSION_CATEGORY_PLAYBACK').which.is.a.String();
			});
		});

		describe.ios('.AUDIO_SESSION_CATEGORY_RECORD', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('AUDIO_SESSION_CATEGORY_RECORD').which.is.a.String();
			});
		});

		describe.ios('.AUDIO_SESSION_CATEGORY_SOLO_AMBIENT', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('AUDIO_SESSION_CATEGORY_SOLO_AMBIENT').which.is.a.String();
			});
		});

		describe.ios('.AUDIO_SESSION_OVERRIDE_ROUTE_NONE', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('AUDIO_SESSION_OVERRIDE_ROUTE_NONE').which.is.a.Number();
			});
		});

		describe.ios('.AUDIO_SESSION_OVERRIDE_ROUTE_SPEAKER', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('AUDIO_SESSION_OVERRIDE_ROUTE_SPEAKER').which.is.a.Number();
			});
		});

		describe.ios('.AUDIO_SESSION_PORT_AIRPLAY', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('AUDIO_SESSION_PORT_AIRPLAY').which.is.a.String();
			});
		});

		describe.ios('.AUDIO_SESSION_PORT_BLUETOOTHA2DP', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('AUDIO_SESSION_PORT_BLUETOOTHA2DP').which.is.a.String();
			});
		});

		describe.ios('.AUDIO_SESSION_PORT_BLUETOOTHHFP', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('AUDIO_SESSION_PORT_BLUETOOTHHFP').which.is.a.String();
			});
		});

		describe.ios('.AUDIO_SESSION_PORT_BLUETOOTHLE', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('AUDIO_SESSION_PORT_BLUETOOTHLE').which.is.a.String();
			});
		});

		describe.ios('.AUDIO_SESSION_PORT_BUILTINMIC', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('AUDIO_SESSION_PORT_BUILTINMIC').which.is.a.String();
			});
		});

		describe.ios('.AUDIO_SESSION_PORT_BUILTINRECEIVER', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('AUDIO_SESSION_PORT_BUILTINRECEIVER').which.is.a.String();
			});
		});

		describe.ios('.AUDIO_SESSION_PORT_BUILTINSPEAKER', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('AUDIO_SESSION_PORT_BUILTINSPEAKER').which.is.a.String();
			});
		});

		describe.ios('.AUDIO_SESSION_PORT_CARAUDIO', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('AUDIO_SESSION_PORT_CARAUDIO').which.is.a.String();
			});
		});

		describe.ios('.AUDIO_SESSION_PORT_HDMI', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('AUDIO_SESSION_PORT_HDMI').which.is.a.String();
			});
		});

		describe.ios('.AUDIO_SESSION_PORT_HEADPHONES', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('AUDIO_SESSION_PORT_HEADPHONES').which.is.a.String();
			});
		});

		describe.ios('.AUDIO_SESSION_PORT_HEADSETMIC', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('AUDIO_SESSION_PORT_HEADSETMIC').which.is.a.String();
			});
		});

		describe.ios('.AUDIO_SESSION_PORT_LINEIN', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('AUDIO_SESSION_PORT_LINEIN').which.is.a.String();
			});
		});

		describe.ios('.AUDIO_SESSION_PORT_LINEOUT', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('AUDIO_SESSION_PORT_LINEOUT').which.is.a.String();
			});
		});

		describe.ios('.AUDIO_SESSION_PORT_USBAUDIO', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('AUDIO_SESSION_PORT_USBAUDIO').which.is.a.String();
			});
		});

		describe('.AUDIO_STATE_BUFFERING', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('AUDIO_STATE_BUFFERING').which.is.a.Number();
			});
		});

		describe('.AUDIO_STATE_INITIALIZED', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('AUDIO_STATE_INITIALIZED').which.is.a.Number();
			});
		});

		describe('.AUDIO_STATE_PAUSED', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('AUDIO_STATE_PAUSED').which.is.a.Number();
			});
		});

		describe('.AUDIO_STATE_PLAYING', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('AUDIO_STATE_PLAYING').which.is.a.Number();
			});
		});

		describe('.AUDIO_STATE_STARTING', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('AUDIO_STATE_STARTING').which.is.a.Number();
			});
		});

		describe('.AUDIO_STATE_STOPPED', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('AUDIO_STATE_STOPPED').which.is.a.Number();
			});
		});

		describe('.AUDIO_STATE_STOPPING', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('AUDIO_STATE_STOPPING').which.is.a.Number();
			});
		});

		describe('.AUDIO_STATE_WAITING_FOR_DATA', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('AUDIO_STATE_WAITING_FOR_DATA').which.is.a.Number();
			});
		});

		describe('.AUDIO_STATE_WAITING_FOR_QUEUE', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('AUDIO_STATE_WAITING_FOR_QUEUE').which.is.a.Number();
			});
		});

		describe.ios('.CAMERA_AUTHORIZATION_AUTHORIZED', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('CAMERA_AUTHORIZATION_AUTHORIZED').which.is.a.Number();
			});
		});

		describe.ios('.CAMERA_AUTHORIZATION_DENIED', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('CAMERA_AUTHORIZATION_DENIED').which.is.a.Number();
			});
		});

		describe.ios('.CAMERA_AUTHORIZATION_RESTRICTED', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('CAMERA_AUTHORIZATION_RESTRICTED').which.is.a.Number();
			});
		});

		describe.ios('.CAMERA_AUTHORIZATION_UNKNOWN', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('CAMERA_AUTHORIZATION_UNKNOWN').which.is.a.Number();
			});
		});

		describe('.CAMERA_FLASH_AUTO', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('CAMERA_FLASH_AUTO').which.is.a.Number();
			});
		});

		describe('.CAMERA_FLASH_OFF', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('CAMERA_FLASH_OFF').which.is.a.Number();
			});
		});

		describe('.CAMERA_FLASH_ON', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('CAMERA_FLASH_ON').which.is.a.Number();
			});
		});

		describe('.CAMERA_FRONT', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('CAMERA_FRONT').which.is.a.Number();
			});
		});

		describe('.CAMERA_REAR', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('CAMERA_REAR').which.is.a.Number();
			});
		});

		describe('.DEVICE_BUSY', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('DEVICE_BUSY').which.is.a.Number();
			});
		});

		describe('.IMAGE_SCALING_AUTO', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('IMAGE_SCALING_AUTO').which.is.a.Number();
			});
		});

		describe('.IMAGE_SCALING_NONE', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('IMAGE_SCALING_NONE').which.is.a.Number();
			});
		});

		describe('.IMAGE_SCALING_FILL', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('IMAGE_SCALING_FILL').which.is.a.Number();
			});
		});

		describe('.IMAGE_SCALING_ASPECT_FILL', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('IMAGE_SCALING_ASPECT_FILL').which.is.a.Number();
			});
		});

		describe('.IMAGE_SCALING_ASPECT_FIT', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('IMAGE_SCALING_ASPECT_FIT').which.is.a.Number();
			});
		});

		describe.ios('.MEDIA_TYPE_LIVEPHOTO', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('MEDIA_TYPE_LIVEPHOTO').which.is.a.String();
			});
		});

		describe('.MEDIA_TYPE_PHOTO', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('MEDIA_TYPE_PHOTO').which.is.a.String();
			});
		});

		describe('.MEDIA_TYPE_VIDEO', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('MEDIA_TYPE_VIDEO').which.is.a.String();
			});
		});

		describe.ios('.MUSIC_MEDIA_GROUP_ALBUM', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('MUSIC_MEDIA_GROUP_ALBUM').which.is.a.Number();
			});

			it('is 1', () => {
				should(Ti.Media.MUSIC_MEDIA_GROUP_ALBUM).eql(1);
			});
		});

		describe.ios('.MUSIC_MEDIA_GROUP_ALBUM_ARTIST', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('MUSIC_MEDIA_GROUP_ALBUM_ARTIST').which.is.a.Number();
			});

			it('is 3', () => {
				should(Ti.Media.MUSIC_MEDIA_GROUP_ALBUM_ARTIST).eql(3);
			});
		});

		describe.ios('.MUSIC_MEDIA_GROUP_ARTIST', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('MUSIC_MEDIA_GROUP_ARTIST').which.is.a.Number();
			});

			it('is 2', () => {
				should(Ti.Media.MUSIC_MEDIA_GROUP_ARTIST).eql(2);
			});
		});

		describe.ios('.MUSIC_MEDIA_GROUP_COMPOSER', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('MUSIC_MEDIA_GROUP_COMPOSER').which.is.a.Number();
			});

			it('is 4', () => {
				should(Ti.Media.MUSIC_MEDIA_GROUP_COMPOSER).eql(4);
			});
		});

		describe.ios('.MUSIC_MEDIA_GROUP_GENRE', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('MUSIC_MEDIA_GROUP_GENRE').which.is.a.Number();
			});

			it('is 5', () => {
				should(Ti.Media.MUSIC_MEDIA_GROUP_GENRE).eql(5);
			});
		});

		describe.ios('.MUSIC_MEDIA_GROUP_PLAYLIST', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('MUSIC_MEDIA_GROUP_PLAYLIST').which.is.a.Number();
			});

			it('is 6', () => {
				should(Ti.Media.MUSIC_MEDIA_GROUP_PLAYLIST).eql(6);
			});
		});

		describe.ios('.MUSIC_MEDIA_GROUP_PODCAST_TITLE', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('MUSIC_MEDIA_GROUP_PODCAST_TITLE').which.is.a.Number();
			});

			it('is 7', () => {
				should(Ti.Media.MUSIC_MEDIA_GROUP_PODCAST_TITLE).eql(7);
			});
		});

		describe.ios('.MUSIC_MEDIA_GROUP_TITLE', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('MUSIC_MEDIA_GROUP_TITLE').which.is.a.Number();
			});
		});

		describe.ios('.MUSIC_MEDIA_TYPE_ALL', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('MUSIC_MEDIA_TYPE_ALL').which.is.a.Number();
			});
		});

		describe.ios('.MUSIC_MEDIA_TYPE_ANY_AUDIO', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('MUSIC_MEDIA_TYPE_ANY_AUDIO').which.is.a.Number();
			});
		});

		describe.ios('.MUSIC_MEDIA_TYPE_AUDIOBOOK', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('MUSIC_MEDIA_TYPE_AUDIOBOOK').which.is.a.Number();
			});
		});

		describe.ios('.MUSIC_MEDIA_TYPE_MUSIC', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('MUSIC_MEDIA_TYPE_MUSIC').which.is.a.Number();
			});
		});

		describe.ios('.MUSIC_MEDIA_TYPE_PODCAST', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('MUSIC_MEDIA_TYPE_PODCAST').which.is.a.Number();
			});
		});

		describe.ios('.MUSIC_PLAYER_REPEAT_ALL', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('MUSIC_PLAYER_REPEAT_ALL').which.is.a.Number();
			});
		});

		describe.ios('.MUSIC_PLAYER_REPEAT_DEFAULT', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('MUSIC_PLAYER_REPEAT_DEFAULT').which.is.a.Number();
			});
		});

		describe.ios('.MUSIC_PLAYER_REPEAT_NONE', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('MUSIC_PLAYER_REPEAT_NONE').which.is.a.Number();
			});
		});

		describe.ios('.MUSIC_PLAYER_REPEAT_ONE', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('MUSIC_PLAYER_REPEAT_ONE').which.is.a.Number();
			});
		});

		describe.ios('.MUSIC_PLAYER_SHUFFLE_ALBUMS', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('MUSIC_PLAYER_SHUFFLE_ALBUMS').which.is.a.Number();
			});
		});

		describe.ios('.MUSIC_PLAYER_SHUFFLE_DEFAULT', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('MUSIC_PLAYER_SHUFFLE_DEFAULT').which.is.a.Number();
			});
		});

		describe.ios('.MUSIC_PLAYER_SHUFFLE_NONE', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('MUSIC_PLAYER_SHUFFLE_NONE').which.is.a.Number();
			});
		});

		describe.ios('.MUSIC_PLAYER_SHUFFLE_SONGS', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('MUSIC_PLAYER_SHUFFLE_SONGS').which.is.a.Number();
			});
		});

		describe.ios('.MUSIC_PLAYER_STATE_INTERRUPTED', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('MUSIC_PLAYER_STATE_INTERRUPTED').which.is.a.Number();
			});
		});

		describe.ios('.MUSIC_PLAYER_STATE_PAUSED', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('MUSIC_PLAYER_STATE_PAUSED').which.is.a.Number();
			});
		});

		describe.ios('.MUSIC_PLAYER_STATE_PLAYING', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('MUSIC_PLAYER_STATE_PLAYING').which.is.a.Number();
			});
		});

		describe.ios('.MUSIC_PLAYER_STATE_SEEK_BACKWARD', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('MUSIC_PLAYER_STATE_SEEK_BACKWARD').which.is.a.Number();
			});
		});

		describe.ios('.MUSIC_PLAYER_STATE_SEEK_FORWARD', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('MUSIC_PLAYER_STATE_SEEK_FORWARD').which.is.a.Number();
			});
		});

		describe.ios('.MUSIC_PLAYER_STATE_STOPPED', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('MUSIC_PLAYER_STATE_STOPPED').which.is.a.Number();
			});
		});

		describe('.NO_CAMERA', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('NO_CAMERA').which.is.a.Number();
			});
		});

		describe('.NO_VIDEO', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('NO_VIDEO').which.is.a.Number();
			});
		});

		describe('.QUALITY_HIGH', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('QUALITY_HIGH').which.is.a.Number();
			});
		});

		describe('.QUALITY_LOW', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('QUALITY_LOW').which.is.a.Number();
			});
		});

		describe.ios('.QUALITY_MEDIUM', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('QUALITY_MEDIUM').which.is.a.Number();
			});
		});

		describe('.UNKNOWN_ERROR', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('UNKNOWN_ERROR').which.is.a.Number();
			});
		});

		describe.android('.VIDEO_CONTROL_DEFAULT', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('VIDEO_CONTROL_DEFAULT').which.is.a.Number();
			});
		});

		describe.android('.VIDEO_CONTROL_EMBEDDED', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('VIDEO_CONTROL_EMBEDDED').which.is.a.Number();
			});
		});

		describe.android('.VIDEO_CONTROL_FULLSCREEN', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('VIDEO_CONTROL_FULLSCREEN').which.is.a.Number();
			});
		});

		describe.android('.VIDEO_CONTROL_HIDDEN', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('VIDEO_CONTROL_HIDDEN').which.is.a.Number();
			});
		});

		describe.android('.VIDEO_CONTROL_NONE', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('VIDEO_CONTROL_NONE').which.is.a.Number();
			});
		});

		describe.android('.VIDEO_FINISH_REASON_PLAYBACK_ENDED', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('VIDEO_FINISH_REASON_PLAYBACK_ENDED').which.is.a.Number();
			});
		});

		describe.android('.VIDEO_FINISH_REASON_PLAYBACK_ERROR', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('VIDEO_FINISH_REASON_PLAYBACK_ERROR').which.is.a.Number();
			});
		});

		describe.android('.VIDEO_FINISH_REASON_USER_EXITED', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('VIDEO_FINISH_REASON_USER_EXITED').which.is.a.Number();
			});
		});

		describe.ios('.VIDEO_LOAD_STATE_FAILED', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('VIDEO_LOAD_STATE_FAILED').which.is.a.Number();
			});
		});

		describe('.VIDEO_LOAD_STATE_PLAYABLE', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('VIDEO_LOAD_STATE_PLAYABLE').which.is.a.Number();
			});
		});

		describe('.VIDEO_LOAD_STATE_UNKNOWN', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('VIDEO_LOAD_STATE_UNKNOWN').which.is.a.Number();
			});
		});

		describe.ios('.VIDEO_MEDIA_TYPE_AUDIO', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('VIDEO_MEDIA_TYPE_AUDIO').which.is.a.String();
			});
		});

		describe.ios('.VIDEO_MEDIA_TYPE_CLOSED_CAPTION', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('VIDEO_MEDIA_TYPE_CLOSED_CAPTION').which.is.a.String();
			});
		});

		describe.ios('.VIDEO_MEDIA_TYPE_DEPTH_DATA', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('VIDEO_MEDIA_TYPE_DEPTH_DATA').which.is.a.String();
			});
		});

		describe.ios('.VIDEO_MEDIA_TYPE_METADATA', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('VIDEO_MEDIA_TYPE_METADATA').which.is.a.String();
			});
		});

		describe.ios('.VIDEO_MEDIA_TYPE_METADATA_OBJECT', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('VIDEO_MEDIA_TYPE_METADATA_OBJECT').which.is.a.String();
			});
		});

		describe.ios('.VIDEO_MEDIA_TYPE_MUXED', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('VIDEO_MEDIA_TYPE_MUXED').which.is.a.String();
			});
		});

		describe.ios('.VIDEO_MEDIA_TYPE_SUBTITLE', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('VIDEO_MEDIA_TYPE_SUBTITLE').which.is.a.String();
			});
		});

		describe.ios('.VIDEO_MEDIA_TYPE_TEXT', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('VIDEO_MEDIA_TYPE_TEXT').which.is.a.String();
			});
		});

		describe.ios('.VIDEO_MEDIA_TYPE_TIMECODE', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('VIDEO_MEDIA_TYPE_TIMECODE').which.is.a.String();
			});
		});

		describe.ios('.VIDEO_MEDIA_TYPE_VIDEO', () => {
			it('is a String', () => {
				should(Ti.Media).have.a.constant('VIDEO_MEDIA_TYPE_VIDEO').which.is.a.String();
			});
		});

		describe('.VIDEO_PLAYBACK_STATE_INTERRUPTED', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('VIDEO_PLAYBACK_STATE_INTERRUPTED').which.is.a.Number();
			});
		});

		describe('.VIDEO_PLAYBACK_STATE_PAUSED', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('VIDEO_PLAYBACK_STATE_PAUSED').which.is.a.Number();
			});
		});

		describe('.VIDEO_PLAYBACK_STATE_PLAYING', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('VIDEO_PLAYBACK_STATE_PLAYING').which.is.a.Number();
			});
		});

		describe.android('.VIDEO_PLAYBACK_STATE_SEEKING_BACKWARD', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('VIDEO_PLAYBACK_STATE_SEEKING_BACKWARD').which.is.a.Number();
			});
		});

		describe.android('.VIDEO_PLAYBACK_STATE_SEEKING_FORWARD', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('VIDEO_PLAYBACK_STATE_SEEKING_FORWARD').which.is.a.Number();
			});
		});

		describe('.VIDEO_PLAYBACK_STATE_STOPPED', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('VIDEO_PLAYBACK_STATE_STOPPED').which.is.a.Number();
			});
		});

		describe('.VIDEO_REPEAT_MODE_NONE', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('VIDEO_REPEAT_MODE_NONE').which.is.a.Number();
			});
		});

		describe('.VIDEO_REPEAT_MODE_ONE', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('VIDEO_REPEAT_MODE_ONE').which.is.a.Number();
			});
		});

		describe.android('.VIDEO_SCALING_ASPECT_FILL', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('VIDEO_SCALING_ASPECT_FILL').which.is.a.Number();
			});
		});

		describe.android('.VIDEO_SCALING_ASPECT_FIT', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('VIDEO_SCALING_ASPECT_FIT').which.is.a.Number();
			});
		});

		describe.android('.VIDEO_SCALING_MODE_FILL', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('VIDEO_SCALING_MODE_FILL').which.is.a.Number();
			});
		});

		describe.android('.VIDEO_SCALING_NONE', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('VIDEO_SCALING_NONE').which.is.a.Number();
			});
		});

		describe('.VIDEO_SCALING_RESIZE', () => {
			it('is a String on iOS, Number on Android', () => {
				if (OS_ANDROID) {
					should(Ti.Media).have.a.constant('VIDEO_SCALING_RESIZE').which.is.a.Number();
				} else {
					should(Ti.Media).have.a.constant('VIDEO_SCALING_RESIZE').which.is.a.String();
				}
			});
		});

		describe('.VIDEO_SCALING_RESIZE_ASPECT', () => {
			it('is a String on iOS, Number on Android', () => {
				if (OS_ANDROID) {
					should(Ti.Media).have.a.constant('VIDEO_SCALING_RESIZE_ASPECT').which.is.a.Number();
				} else {
					should(Ti.Media).have.a.constant('VIDEO_SCALING_RESIZE_ASPECT').which.is.a.String();
				}
			});
		});

		describe('.VIDEO_SCALING_RESIZE_ASPECT_FILL', () => {
			it('is a String on iOS, Number on Android', () => {
				if (OS_ANDROID) {
					should(Ti.Media).have.a.constant('VIDEO_SCALING_RESIZE_ASPECT_FILL').which.is.a.Number();
				} else {
					should(Ti.Media).have.a.constant('VIDEO_SCALING_RESIZE_ASPECT_FILL').which.is.a.String();
				}
			});
		});

		describe.android('.VIDEO_TIME_OPTION_CLOSEST_SYNC', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('VIDEO_TIME_OPTION_CLOSEST_SYNC').which.is.a.Number();
			});
		});

		describe.ios('.VIDEO_TIME_OPTION_EXACT', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('VIDEO_TIME_OPTION_EXACT').which.is.a.Number();
			});
		});

		describe('.VIDEO_TIME_OPTION_NEAREST_KEYFRAME', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('VIDEO_TIME_OPTION_NEAREST_KEYFRAME').which.is.a.Number();
			});
		});

		describe.android('.VIDEO_TIME_OPTION_NEXT_SYNC', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('VIDEO_TIME_OPTION_NEXT_SYNC').which.is.a.Number();
			});
		});

		describe.android('.VIDEO_TIME_OPTION_PREVIOUS_SYNC', () => {
			it('is a Number', () => {
				should(Ti.Media).have.a.constant('VIDEO_TIME_OPTION_PREVIOUS_SYNC').which.is.a.Number();
			});
		});
	});
});
