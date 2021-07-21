/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2020-Present by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
/* eslint-env mocha */
/* eslint no-unused-expressions: "off" */
/* eslint no-undef: "off" */
'use strict';

const should = require('./utilities/assertions');

describe('Titanium.UI.Animation', () => {
	let instance;

	beforeEach(() => {
		// TODO: Create an instance of the type!
		instance = Ti.UI.createAnimation();
	});

	afterEach(() => {
		// TODO: Do any cleanup of the instance we need to do!
		instance = null;
	});

	describe('properties', () => {
		describe.android('.anchorPoint', () => {
			it('is a Point', () => {
				instance.anchorPoint = { x: 0, y: 0 };
				should(instance).have.a.property('anchorPoint').which.is.an.Object();
				should(instance.anchorPoint).have.properties(['x', 'y']);
			});
		});

		describe('.apiName', () => {
			it('is a String', () => {
				should(instance).have.a.readOnlyProperty('apiName').which.is.a.String();
			});

			it('equals Ti.UI.Animation', () => {
				should(instance.apiName).eql('Ti.UI.Animation');
			});
		});

		describe('.autoreverse', () => {
			it('is a Boolean', () => {
				should(instance).have.a.property('autoreverse').which.is.a.Boolean();
			});

			it('defaults to false', () => {
				should(instance.autoreverse).be.false();
			});
		});

		describe('.backgroundColor', () => {
			it('is a String', () => {
				instance.backgroundColor = 'black';
				should(instance)
					.have.a.property('backgroundColor').which.is.a.String();
			});
		});

		describe('.bottom', () => {
			it('is a Number', () => {
				instance.bottom = 0;
				should(instance).have.a.property('bottom').which.is.a.Number();
			});
		});

		describe('.center', () => {
			it('is a Point', () => {
				instance.center = { x: 50, y: 50 };
				should(instance).have.a.property('center').which.is.an.Object();
			});
		});

		describe('.color', () => {
			it('is a String', () => {
				instance.color = 'green';
				should(instance).have.a.property('color').which.is.a.String();
			});
		});

		describe('.curve', () => {
			it('is a Number', () => {
				instance.curve = Titanium.UI.ANIMATION_CURVE_EASE_IN;
				should(instance).have.a.property('curve').which.is.a.Number();
			});

			it('is one of Ti.UI.ANIMATION_CURVE_EASE_*', () => {
				instance.curve = Titanium.UI.ANIMATION_CURVE_EASE_IN_OUT;
				should([
					Ti.UI.ANIMATION_CURVE_EASE_IN,
					Ti.UI.ANIMATION_CURVE_EASE_IN_OUT,
					Ti.UI.ANIMATION_CURVE_EASE_OUT,
					Ti.UI.ANIMATION_CURVE_LINEAR,
				]).containEql(instance.curve);
			});
		});

		describe.ios('.dampingRatio', () => {
			it('is a Number', () => {
				instance.dampingRatio = 0.8;
				should(instance).have.a.property('dampingRatio').which.is.a.Number();
			});
		});

		describe('.delay', () => {
			it('is a Number', () => {
				instance.delay = 200;
				should(instance).have.a.property('delay').which.is.a.Number();
			});
		});

		describe('.duration', () => {
			it('is a Number', () => {
				instance.duration = 200;
				should(instance).have.a.property('duration').which.is.a.Number();
			});
		});

		describe.android('.elevation', () => {
			it('is a Number', () => {
				instance.elevation = 3;
				should(instance).have.a.property('elevation').which.is.a.Number();
			});
		});

		describe('.height', () => {
			it('is a Number', () => {
				instance.height = 100;
				should(instance).have.a.property('height').which.is.a.Number();
			});
		});

		describe('.left', () => {
			it('is a Number', () => {
				instance.left = 0;
				should(instance).have.a.property('left').which.is.a.Number();
			});
		});

		describe('.opacity', () => {
			it('is a Number', () => {
				instance.opacity = 0.5;
				should(instance).have.a.property('opacity').which.is.a.Number();
			});
		});

		describe.ios('.opaque', () => {
			it('is a Boolean', () => {
				instance.opaque = false;
				should(instance).have.a.property('opaque').which.is.a.Boolean();
			});
		});

		describe('.repeat', () => {
			it('is a Number', () => {
				should(instance).have.a.property('repeat').which.is.a.Number();
			});

			it('defaults to 1 (no repeat)', () => {
				should(instance.repeat).eql(1);
			});
		});

		describe('.right', () => {
			it('is a Number', () => {
				instance.right = 20;
				should(instance).have.a.property('right').which.is.a.Number();
			});
		});

		describe.ios('.springVelocity', () => {
			it('is a Number', () => {
				instance.springVelocity = 0.5;
				should(instance).have.a.property('springVelocity').which.is.a.Number();
			});
		});

		describe('.top', () => {
			it('is a Number', () => {
				instance.top = 40;
				should(instance).have.a.property('top').which.is.a.Number();
			});
		});

		describe('.transform', () => {
			it('is a Titanium.UI.Matrix2D,Titanium.UI.Matrix3D', () => {
				instance.transform = Ti.UI.createMatrix2D({
					scale: 2
				});
				should(instance).have.a.property('transform').which.is.an.Object();
				const matrixApiName = Ti.Platform.osname === 'android' ? 'Ti.UI.Matrix2D' : 'Ti.UI.2DMatrix';
				should(instance.transform.apiName).eql(matrixApiName);
			});
		});

		describe.ios('.transition', () => {
			it('is a Number', () => {
				instance.transition = Ti.UI.iOS.AnimationStyle.CROSS_DISSOLVE;
				should(instance).have.a.property('transition').which.is.a.Number();
			});

			it('is one of Ti.UI.iOS.AnimationStyle.CURL_*', () => {
				instance.transition = Ti.UI.iOS.AnimationStyle.FLIP_FROM_TOP;
				should([
					Ti.UI.iOS.AnimationStyle.CURL_DOWN,
					Ti.UI.iOS.AnimationStyle.CURL_UP,
					Ti.UI.iOS.AnimationStyle.FLIP_FROM_LEFT,
					Ti.UI.iOS.AnimationStyle.FLIP_FROM_RIGHT,
					Ti.UI.iOS.AnimationStyle.FLIP_FROM_TOP,
					Ti.UI.iOS.AnimationStyle.FLIP_FROM_BOTTOM,
					Ti.UI.iOS.AnimationStyle.CROSS_DISSOLVE,
					Ti.UI.iOS.AnimationStyle.NONE,
				]).containEql(instance.transition);
			});
		});

		describe.ios('.view', () => {
			it('is a Titanium.UI.View', () => {
				instance.view = Ti.UI.createView();
				should(instance).have.a.property('view').which.is.an.Object();
				should(instance.view).have.a.property('apiName').which.is.eql('Ti.UI.View');
			});
		});

		describe.ios('.visible', () => {
			it('is a Boolean', () => {
				instance.visible = false;
				should(instance).have.a.property('visible').which.is.a.Boolean();
			});
		});

		describe('.width', () => {
			it('is a Number', () => {
				instance.width = 256;
				should(instance).have.a.property('width').which.is.a.Number();
			});
		});

		describe.ios('.zIndex', () => {
			it('is a Number', () => {
				instance.zIndex = 2;
				should(instance).have.a.property('zIndex').which.is.a.Number();
			});
		});
	});

	describe('examples', () => {
		let win;
		let container;

		function once(target, name, listener) {
			target.addEventListener(name, e => {
				target.removeEventListener(name, listener);
				listener(e);
			});
		}

		afterEach(done => {
			if (win && !win.closed) {
				win.addEventListener('close', function listener () {
					win.removeEventListener('close', listener);
					container = null;
					win = null;
					done();
				});
				console.log('win.close()');
				win.close();
			} else {
				container = null;
				win = null;
				done();
			}
		});

		beforeEach(() => {
			win = Ti.UI.createWindow();
			container = Ti.UI.createView({
				width: 300,
				height: 300,
				backgroundColor: 'white'
			});
			win.add(container);
		});

		it('Simple Animation Applied to a View', done => {
			var view = Titanium.UI.createView({
				backgroundColor: 'red'
			});
			container.add(view);
			once(win, 'postlayout', () => {
				should(container).matchImage('snapshots/animation_simple_start.png');
				var animation = Titanium.UI.createAnimation();
				animation.backgroundColor = 'black';
				animation.duration = 500;
				function animationHandler() {
					should(container).matchImage('snapshots/animation_simple_mid.png');
					animation.removeEventListener('complete', animationHandler);
					animation.backgroundColor = 'orange';
					view.animate(animation, () => {
						should(container).matchImage('snapshots/animation_simple_end.png');
						done();
					});
				}
				animation.addEventListener('complete', animationHandler);
				view.animate(animation);
			});
			win.open();
		});

		it('Animation Using Matrix Transforms', done => {
			const box = Ti.UI.createView({
				backgroundColor: 'red',
				height: '100',
				width: '100'
			});
			container.add(box);
			once(win, 'postlayout', () => {
				should(container).matchImage('snapshots/animation_matrix_start.png');
				let matrix = Ti.UI.createMatrix2D();
				matrix = matrix.rotate(45);
				matrix = matrix.scale(2, 2);
				const a = Ti.UI.createAnimation({
					transform: matrix
				});
				a.addEventListener('complete', () => {
					should(container).matchImage('snapshots/animation_matrix_end.png');
					done();
				});
				box.animate(a);
			});
			win.open();
		});

		it('Using an anchorPoint (Android and iOS)', done => {
			const anchorPoint = { x: 1, y: 0 }; // top-rght
			let t = Ti.UI.createMatrix2D();
			t = t.rotate(90);
			const view = Ti.UI.createView({
				backgroundColor: '#336699',
				width: 100,
				height: 100
			});
			container.add(view);
			const a = Ti.UI.createAnimation({
				transform: t,
				anchorPoint
			});
			// set new anchorPoint on view for iOS
			view.anchorPoint = anchorPoint;
			once(win, 'postlayout', () => {
				should(container).matchImage('snapshots/animation_anchorPoint_start.png');
				view.animate(a, () => {
					should(container).matchImage('snapshots/animation_anchorPoint_end.png');
					done();
				});
			});
			win.open();
		});

		describe('Updates view properties after animation', () => {
			it('Updates properties from creation dict', done => {
				const box = Ti.UI.createLabel({
					backgroundColor: 'red',
					color: 'blue',
					height: '100',
					width: '100',
					text: 'Box'
				});
				container.add(box);
				let matrix = Ti.UI.createMatrix2D();
				matrix = matrix.rotate(45);
				const a = Ti.UI.createAnimation({
					transform: matrix,
					backgroundColor: 'green',
					color: 'orange'
				});
				once(win, 'postlayout', () => {
					box.animate(a, () => {
						should(box.transform).be.eql(matrix);
						should(box.backgroundColor).be.eql('green');
						should(box.color).be.eql('orange');
						done();
					});
				});
				win.open();
			});

			it('Updates properties set on animation after creation', done => {
				const box = Ti.UI.createLabel({
					backgroundColor: 'red',
					color: 'blue',
					height: '100',
					width: '100',
					text: 'Box'
				});
				container.add(box);
				let matrix = Ti.UI.createMatrix2D();
				matrix = matrix.rotate(45);
				const a = Ti.UI.createAnimation({
					transform: matrix,
				});
				a.backgroundColor = 'green';
				a.color = 'orange';
				once(win, 'postlayout', () => {
					box.animate(a, () => {
						should(box.transform).be.eql(matrix);
						should(box.backgroundColor).be.eql('green');
						should(box.color).be.eql('orange');
						done();
					});
				});
				win.open();
			});
		});
	});
});
