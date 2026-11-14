#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// Runs block. An Objective-C exception it raises (MapLibre raises NSInvalidArgumentException for a filter or a
/// property value it can't take) comes back as "name: reason" instead of ending the app; nil when it ran through.
/// Swift can't catch these itself.
NSString * _Nullable KavCatch(NS_NOESCAPE void (^block)(void));

NS_ASSUME_NONNULL_END
