Ti._5.createClass('Titanium.UI.ScrollView', function(args){
	var obj = this;
	
	// Set defaults
	args = Ti._5.extend({}, args);
	args.unselectable = true;
	args.width = args.width || '100%';
	args.height = args.height || '100%';
		
	// Interfaces
	// outer container
	Ti._5.DOMView(this, 'div', args, 'ScrollView');
	Ti._5.Clickable(this);
	Ti._5.Touchable(this, args);
	Ti._5.Styleable(this, args);
	Ti._5.Positionable(this, args);
	
	this.dom.style.position = 'absolute';
	this.dom.style.overflow = "auto";

	// we need to do some DOM manipulations here - ScrollView needs to have 2 containers - outer for setting contentWidth && contentHeight,
	// and inner one - to apply everything else
	// inner container
	var _innerContainer = document.createElement('div');
	_innerContainer.style.overflow = "hidden";
	_innerContainer.style.position = "absolute";
	obj.dom.appendChild(_innerContainer);
	this._getAddContainer = function(){
		return _innerContainer;
	};
	// Properties
	this.canCancelEvents = true;
	var _contentHeight;
	Object.defineProperty(this, 'contentHeight', {
		get: function(){return _contentHeight;},
		set: function(val){_contentHeight = val; this._getAddContainer().style.height = Ti._5.parseLength(val);}
	});

	var _contentOffset = null;
	Object.defineProperty(this, 'contentOffset', {
		get: function(){return _contentOffset;},
		set: function(val){
			_contentOffset = val;
			if(typeof val.x !== 'undefined'){
				obj.dom.style.paddingLeft = Ti._5.parseLength(val.x);
			}
			if(typeof val.y !== 'undefined'){
				obj.dom.style.paddingTop = Ti._5.parseLength(val.y);
			}
		}
	});

	var _contentWidth;
	Object.defineProperty(this, 'contentWidth', {
		get: function(){return _contentWidth;},
		set: function(val){_contentWidth = val; this._getAddContainer().style.width = Ti._5.parseLength(val);}
	});

	this.disableBounce = false;
	this.horizontalBounce = false;
	this.maxZoomScale = null;
	this.minZoomScale = null;
	this.scrollType = null;

	var _showHorizontalScrollIndicator = null;
	Object.defineProperty(this, 'showHorizontalScrollIndicator', {
		get: function(){return _showHorizontalScrollIndicator;},
		set: function(val){_showHorizontalScrollIndicator = val; obj.dom.style.overflowX = _showHorizontalScrollIndicator ? "scroll" : "hidden";}
	});

	var _showVerticalScrollIndicator = null;
	Object.defineProperty(this, 'showVerticalScrollIndicator', {
		get: function(){return _showVerticalScrollIndicator;},
		set: function(val){_showVerticalScrollIndicator = val; obj.dom.style.overflowY = _showVerticalScrollIndicator ? "scroll" : "hidden";}
	});

	var _size;
	Object.defineProperty(this, 'size', {
		get: function(){return _size;},
		set: function(val){
			if(val != null && val.width != null){
				_innerContainer.style.width = Ti._5.parseLength(val.width);
			}
			if(val != null && val.height != null){
				_innerContainer.style.height = Ti._5.parseLength(val.height);
			}
		}
	});

	this.verticalBounce = null;
	this.zoomScale = null;

	// Methods
	this.scrollTo = function(x, y){
		if(x != null){
			obj.dom.scrollLeft = parseInt(x);
		}
		if(y != null){
			obj.dom.scrollTop = parseInt(y);
		}
	};

	// Events
	obj.dom.addEventListener('scroll', function(event) {
		var undef;
		var oEvent = {
			decelerating: undef,
			dragging	: undef,
			source		: event.target,
			type		: event.type,
			x			: event.pageX,
			y			: event.pageY
		};
		obj.fireEvent('scroll', oEvent);
	}, false);
	Ti._5.preset(this, ['contentHeight', 'contentWidth', 'contentOffset', 'showHorizontalScrollIndicator', 'showVerticalScrollIndicator', 'size'], args)
	Ti._5.presetUserDefinedElements(this, args);
});
