/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 * 
 * WARNING: This is generated code. Modify at your own risk and without support.
 */

#ifdef USE_TI_NETWORK

#import "TiNetworkHTTPClientResultProxy.h"
#import "TiNetworkPersistentHTTPClientProxy.h"
 @implementation TiNetworkPersistentHTTPClientProxy

-(id)init
{
	if (self = [super init])
	{
	}
	return self;
}

-(void)setResponseHandlersFroRequest:(ASIFormDataRequest*)request
{
	if (hasOnsendstream)
	{
		[request setUploadProgressDelegate:self];
	}
	if (hasOndatastream)
	{
		[request setDidReceiveDataSelector:@selector(request:receivedData:)];
	}
}

// Called when the request receives some data - bytes is the length of that data
- (void)request:(ASIHTTPRequest *)request receivedData:(NSData *)data
{
	if (hasOndatastream)
	{
		TiNetworkHTTPClientResultProxy *thisPointer = [[TiNetworkHTTPClientResultProxy alloc] initWithDelegate:self];
        TiBlob* dataBlob = [[[TiBlob alloc] initWithData:data mimetype:@"application/octet-stream"] autorelease];

		NSDictionary *event = [NSDictionary dictionaryWithObjectsAndKeys:dataBlob,@"blob",@"datastream",@"type",nil];
		[self fireCallback:@"ondatastream" withArg:event withSource:thisPointer];
		[thisPointer release];
	}
}

@end

#endif
