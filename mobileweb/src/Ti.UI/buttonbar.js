Ti._5.createClass('Titanium.UI.ButtonBar', function(args){
	var obj = this;
	// Interfaces
	Ti._5.DOMView(this, 'buttonbar', args, 'ButtonBar');
	Ti._5.Touchable(this, args);
	Ti._5.Styleable(this, args);
	Ti._5.Positionable(this, args);

	// Properties
	Ti._5.member(this, 'index');

	Ti._5.member(this, 'labels');

	Ti._5.member(this, 'style');

	require.mix(this, args);
});
