Ti._5.createClass('Titanium.UI.ScrollableView', function(args){
	var obj = this;
	// Interfaces
	Ti._5.DOMView(this, 'div', args, 'ScrollableView');
	Ti._5.Touchable(this, args);
	Ti._5.Styleable(this, args);
	Ti._5.Positionable(this, args);	
	
	this.dom.style.position = 'absolute';
	this.dom.style.overflow = 'hidden';

	// Properties
	var _currentPage = args.currentPage || -1;
	Ti._5.prop(this, 'currentPage', {
		get: function(){return _currentPage;},
		set: function(val){
			if (val >= 0 && val < obj.views.length) {
				obj._scrollToViewPosition(val);
				return _currentPage = val;
			}
			return null;
		}
	});

	Ti._5.member(this, 'maxZoomScale');

	Ti._5.member(this, 'minZoomScale');

	Ti._5.member(this, 'pagingControlColor');

	Ti._5.member(this, 'pagingControlHeight');

	Ti._5.member(this, 'showPagingControl');

	Ti._5.member(this, 'views', []);

	// Methods
	this.addView = function(view){
		
		// Sanity check
		var undef; 
		if (view !== undef && view !== null) {
			obj.views.push(view);
			
			// Check if any children have been added yet, and if not load this view
			if (_currentPage == -1) {
				obj._scrollToViewPosition(0);
			}
		}
	};
	this._viewToRemoveAfterScroll = -1;
	this._removeViewFromList = function(viewIndex) {
		
		// Remove the view
		obj.views.splice(viewIndex,1);
		
		// Update the current view if necessary
		if (viewIndex < _currentPage){
			_currentPage--;
		}
	}
	this.removeView = function(view){
		
		// Get and validate the location of the view
		var viewIndex = obj.views.indexOf(view);
		if (viewIndex == -1) {
			return;
		}
		
		// Update the view if this view was currently visible
		if (viewIndex == _currentPage) {
			this._viewToRemoveAfterScroll = viewIndex;
			if (obj.views.length == 1) {
				obj._removeViewFromList(viewIndex);
				obj.dom.removeChild(obj.dom.firstChild);
			} else {
			    obj._scrollToViewPosition(viewIndex == obj.views.length -1 ? --viewIndex : ++viewIndex);
			}
		} else {
			obj._removeViewFromList(viewIndex);
		}
	};
	this.scrollToView = function(view){
		this._scrollToViewPosition(obj.views.indexOf(view))
	};
	var _interval = null;
	this._scrollToViewPosition = function(viewIndex){
		
		// Sanity check
		if (viewIndex < 0 || viewIndex >= obj.views.length || viewIndex == _currentPage) {
			return;
		}
		
		obj._attachFinalView = function(view) {
		
			// Remove the previous container
			if (obj.dom.childNodes.length > 0) {
				obj.dom.removeChild(obj.dom.firstChild);
			}
			
			// Attach the new container
			var _contentContainer = document.createElement('div');
			_contentContainer.style.position = 'absolute';
			_contentContainer.style.width = '100%';
			_contentContainer.style.height = '100%';
			_contentContainer.appendChild(view);
			obj.dom.appendChild(_contentContainer);
		};
		
		// If the scrollableView hasn't been laid out yet, we can't do much since the scroll distance is unknown.
		// At the same time, it doesn't matter since the user won't see it anyways. So we just append the new
		// element and don't show the transition animation.
		if (!obj.dom.offsetWidth) {
			obj._attachFinalView(obj.views[viewIndex].dom);
		} else {
			
			// Stop the previous timer if it is running (i.e. we are in the middle of an animation)
			_interval && clearInterval(_interval);
		
			// Remove the previous container
			if (obj.dom.childNodes.length) {
				obj.dom.removeChild(obj.dom.firstChild);
			}
			
			// Calculate the views to be scrolled
			var _w = obj.dom.offsetWidth,
				_viewsToScroll = [],
				_scrollingDirection = -1,
				_initialPosition = 0;
			if (viewIndex > _currentPage) {
				for (var i = _currentPage; i <= viewIndex; i++) {
					_viewsToScroll.push(obj.views[i].dom);
				}
			} else {
				for (var i = viewIndex; i <= _currentPage; i++) {
					_viewsToScroll.push(obj.views[i].dom);
				}
				_initialPosition = -(_viewsToScroll.length - 1) * _w;
				_scrollingDirection = 1;
			}
			
			// Create the animation div
			var _contentContainer = document.createElement('div');
			_contentContainer.style.position = 'absolute';
			_contentContainer.style.width = _viewsToScroll.length * _w;
			_contentContainer.style.height = '100%';
			obj.dom.appendChild(_contentContainer);
			
			// Attach the child views, each contained in their own div so we can mess with positioning w/o touching the views
			for (var i = 0; i < _viewsToScroll.length; i++) {
				var _viewDiv = document.createElement('div');
				_viewDiv.style.position = 'absolute';
				_viewDiv.style.width = _w + 'px';
				_viewDiv.style.height = '100%';
				_viewDiv.appendChild(_viewsToScroll[i]);
				_contentContainer.appendChild(_viewDiv);
				_viewDiv.style.left = i * _w + 'px';
			}
			
			// Attach the div to the scrollableView
			obj.dom.appendChild(_contentContainer);
			_contentContainer.style.left = _initialPosition + 'px';
			
			// Set the start time
			var _startTime = (new Date()).getTime(),
				_duration = 300 + 0.2 * _w, // Calculate a weighted duration so that larger views take longer to scroll.
				_distance = (_viewsToScroll.length - 1) * _w;
			
			// Start the timer
			_interval = setInterval(function(){
				
				// Calculate the new position
				var _currentTime = ((new Date()).getTime() - _startTime),
					_normalizedTime = _currentTime / (_duration / 2),
					_newPosition;
				if (_normalizedTime < 1) {
					_newPosition = _distance / 2 * _normalizedTime * _normalizedTime;
				} else {
					_normalizedTime--;
					_newPosition = -_distance / 2 * (_normalizedTime * (_normalizedTime - 2) - 1);
				}
				
				// Update the position of the div
				_contentContainer.style.left = _scrollingDirection * Math.round(_newPosition) + _initialPosition + 'px';
				
				// Check if the transition is finished.
				if (_currentTime >= _duration) {
					clearInterval(_interval);
					_interval = null;
					obj._attachFinalView(obj.views[viewIndex].dom);
					if (obj._viewToRemoveAfterScroll != -1) {
						obj._removeViewFromList(obj._viewToRemoveAfterScroll);
						obj._viewToRemoveAfterScroll = -1;
					}
		    	}
			},32); // Update around 32 FPS.
		}
		_currentPage = viewIndex;
	};
	
	// If some views were defined via args, process them now
	if (obj.views.length > 0) {
		this._scrollToViewPosition(_currentPage != -1 ? _currentPage : 0);
	}

	// Events
	this.addEventListener('scroll', function(){
		console.debug('Event "scroll" is not implemented yet.');
	});

	require.mix(this, args);
});