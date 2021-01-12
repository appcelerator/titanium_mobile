/**
* Axway Titanium Mobile
* Copyright (c) 2020-Present by Axway, Inc. All Rights Reserved.
* Licensed under the terms of the Apache Public License
* Please see the LICENSE included with this distribution for details.
*/

import Foundation
import SwiftUI

// MARK: View controller

@available(iOS 13.0, *)
public class TiErrorViewController : UIViewController {
  let error: TiScriptError
  
  var model: ErrorModel!
  
  @objc
  public init(error: TiScriptError) {
    self.error = error
    super.init(nibName: nil, bundle: nil)
    self.model = ErrorModel(error: error, modalVC: self)
  }
  
  required public override init(nibName nibNameOrNil: String?, bundle nibBundleOrNil: Bundle?) {
    fatalError("init(nibName:bundle:) has not been implemented")
  }
  
  required init?(coder: NSCoder) {
    fatalError("init(coder:) has not been implemented")
  }
  
  public override func viewDidLoad() {
    super.viewDidLoad()
    
    addErrorView()
  }
  
  func addErrorView() {
    let errorView = ErrorView(error: model)
    let hostingController = UIHostingController(rootView: errorView)
    hostingController.view.translatesAutoresizingMaskIntoConstraints = false
    self.addChild(hostingController)
    self.view.addSubview(hostingController.view)
    hostingController.didMove(toParent: self)
    self.parent?.navigationController?.navigationBar.titleTextAttributes = [NSAttributedString.Key.foregroundColor: ErrorTheme.error]
    
    NSLayoutConstraint.activate([
      hostingController.view.widthAnchor.constraint(equalTo: view.widthAnchor),
      hostingController.view.heightAnchor.constraint(equalTo: view.heightAnchor),
      hostingController.view.centerXAnchor.constraint(equalTo: view.centerXAnchor),
      hostingController.view.centerYAnchor.constraint(equalTo: view.centerYAnchor),
    ])
  }
}

// MARK: SwiftUI

@available(iOS 13.0, *)
struct ErrorView : View {
  var error: ErrorModel
  
  var body: some View {
    VStack {
      ScrollView {
        VStack(alignment: .leading) {
          HStack {
            VStack(alignment: .leading, spacing: 8) {
              Text(error.name)
                .font(.system(size: 20, weight: .medium))
                .foregroundColor(Color.init(ErrorTheme.error))
              Text(error.message)
                .font(.subheadline)
            }
            .padding()
            Spacer()
          }
          .background(Color.init(ErrorTheme.darkBg))
          
          ErrorTraitView(trait: error.scriptTrait)
          ErrorTraitView(trait: error.nativeTrait);
        }
      }
      
      Spacer()
        
      Button(action: {
        TiApp.sharedApp()?.hideModalController(error.modalVC.parent?.navigationController, animated: true)
      }) {
        Text("Dismiss")
          .frame(minWidth:0, maxWidth: .infinity, minHeight: 50)
          .background(Color.init(ErrorTheme.error))
          .foregroundColor(.white)
          .font(.system(size: 18))
      }
    }
    .edgesIgnoringSafeArea(.bottom)
    .background(Color.init(ErrorTheme.primaryBg))
  }
}

@available(iOS 13, *)
struct ErrorTraitView: View {
  var trait: ErrorTrait
  var body: some View {
    VStack(alignment: .leading) {
      Text(trait.label)
        .font(.system(size: 22, weight: .medium))
      
      if let sourceLine = trait.sourceLine, let location = trait.location {
        Text("Source")
          .font(.system(size: 17))
          .padding(.top, 1)
        VStack(alignment:.leading) {
          ScrollView(.horizontal, showsIndicators: false) {
            HStack {
              Text(sourceLine)
                .font(.system(.subheadline, design: .monospaced))
              Spacer()
            }
          }
        }.padding()
        .background(Color.init(ErrorTheme.darkBg))
        Text(location)
          .font(.caption)
          .foregroundColor(Color.init(ErrorTheme.lightText))
      }
      StackView(stack: trait.stack)
    }
    .padding(.bottom)
    .padding(.horizontal)
  }
}

struct ErrorTrait {
  var label: String
  var sourceLine: String?
  var location: String?
  var stack: [StackEntry]
}

@available(iOS 13, *)
struct StackView: View {
  var stack: [StackEntry]
  
  var body: some View {
    VStack(alignment: .leading) {
      Text("Stack")
        .padding(.top, 1)
      ScrollView(.horizontal, showsIndicators: false) {
        VStack(alignment: .leading, spacing: 10) {
          ForEach(stack, id: \.self) { entry in
            VStack(alignment:.leading) {
              Text(entry.symbol)
                .font(.system(.subheadline, design: .monospaced))
                .foregroundColor(Color.init(ErrorTheme.text))
              Text(entry.source)
                .font(.system(.caption, design: .monospaced))
                .foregroundColor(Color.init(ErrorTheme.lightText))
            }
          }
        }
      }
      .padding(.horizontal)
    }
  }
}

struct StackEntry: Hashable {
  var symbol: String
  var source: String
}

@available(iOS 13.0, *)
struct ErrorModel {
  var name: String
  var message: String
  var scriptTrait: ErrorTrait
  var nativeTrait: ErrorTrait
  var modalVC: UIViewController
  
  init(error: TiScriptError, modalVC: UIViewController) {
    let errorDict = error.dictionaryValue
    self.name = errorDict?["type"] as? String ?? "Error"
    
    self.message = error.message
    
    let scriptSourceLine = error.sourceLine ?? "Could not load source"
    let scriptStack = error.parsedJsStack!.map({ (entry: [AnyHashable : Any]) -> StackEntry in
      return StackEntry(symbol: entry["symbol"] as! String, source: entry["source"] as! String)
    })
    let firstScriptSourceIndex = scriptStack.firstIndex { (entry) -> Bool in
      return entry.source != "[native code]"
    } ?? 0
    let scriptLocation = scriptStack[firstScriptSourceIndex].source
    self.scriptTrait = ErrorTrait(label: "JavaScript",
                                  sourceLine: scriptSourceLine,
                                  location: scriptLocation,
                                  stack: scriptStack)
    
    let nativeStack = error.formattedNativeStack!.map({ (line) -> StackEntry in
      let components = line.split(separator: " ")
      let symbol = components[2...].joined(separator: " ");
      let source = components[...1].joined(separator: " ");
      return StackEntry(symbol: symbol, source: source)
    })
    var nativeSourceLine: String?
    var nativeLocation: String?
    if let rawLocation = errorDict?["nativeLocation"] as? String {
      let regex = try! NSRegularExpression(pattern: #"(.*)\s\((.*:\d+)\)"#, options: [])
      let nsrange = NSRange(rawLocation.startIndex..<rawLocation.endIndex,
                            in: rawLocation)
      regex.enumerateMatches(in: rawLocation, options: [], range: nsrange) { (match, _, _) in
        guard let match = match else {
          return
        }
        
        if match.numberOfRanges == 3,
          let firstRange = Range(match.range(at: 1), in: rawLocation),
          let secondRange = Range(match.range(at: 2), in: rawLocation)
        {
          nativeSourceLine = String(rawLocation[firstRange])
          nativeLocation = String(rawLocation[secondRange])
        }
      }
    }
    self.nativeTrait = ErrorTrait(label: "Native",
                                  sourceLine: nativeSourceLine,
                                  location: nativeLocation,
                                  stack: nativeStack)
    
    self.modalVC = modalVC
  }
}

@available(iOS 13.0, *)
struct ErrorView_Previews: PreviewProvider {
  static var previews: some View {
    let error = TiScriptError(dictionary: [
      "type": "Error",
      "message": "test error",
      "backtrace": "app.js:1:133\nglobal code@app.js:2:70\nrequire@[native code]\nti.main.js:14518:10\nloadAsync@ti.main.js:14446:13\nglobal code@ti.main.js:14515:10"
    ])!
    let model = ErrorModel(error: error, modalVC: UIViewController())
    return ErrorView(error: model).environment(\.colorScheme, .dark)
  }
}

// MARK: UI Theme & Colors

fileprivate enum MaterialUI {
  static let red = UIColor(red: 0xf4 / 0xff,
                           green: 0x43 / 0xff,
                           blue: 0x36 / 0xff,
                           alpha: 1)
  static let redLighten1 = UIColor(red: 0xef / 0xff,
                                   green: 0x53 / 0xff,
                                   blue: 0x50 / 0xff,
                                   alpha: 1)
  static let greyLighten1 = UIColor(red: 0xbd / 0xff,
                                    green: 0xbd / 0xff,
                                    blue: 0xbd / 0xff,
                                    alpha: 1)
  static let greyLighten4 = UIColor(red: 0xf5 / 0xff,
                                    green: 0xf5 / 0xff,
                                    blue: 0xf5 / 0xff,
                                    alpha: 1)
  static let greyDarken1 = UIColor(red: 0x75 / 0xff,
                                   green: 0x75 / 0xff,
                                   blue: 0x75 / 0xff,
                                   alpha: 1)
  static let greyDarken3 = UIColor(red: 0x42 / 0xff,
                                   green: 0x42 / 0xff,
                                   blue: 0x42 / 0xff,
                                   alpha: 1)
  static let greyDarken4 = UIColor(red: 0x21 / 0xff,
                                   green: 0x21 / 0xff,
                                   blue: 0x21 / 0xff,
                                   alpha: 1)
}

@available(iOS 13, *)
class ErrorTheme {
  static var error: UIColor {
    return UIColor { (traitCollection: UITraitCollection) -> UIColor in
      if traitCollection.userInterfaceStyle == .dark {
          return MaterialUI.redLighten1
      } else {
          return MaterialUI.red
      }
    }
  }
  static var primaryBg: UIColor {
    return UIColor { (traitCollection: UITraitCollection) -> UIColor in
      if traitCollection.userInterfaceStyle == .dark {
          return UIColor(red: 0x39 / 0xff,
                         green: 0x39 / 0xff,
                         blue: 0x39 / 0xff,
                         alpha: 1)
      } else {
        return UIColor.white
      }
    }
  }
  static var darkBg: UIColor {
    return UIColor { (traitCollection: UITraitCollection) -> UIColor in
      if traitCollection.userInterfaceStyle == .dark {
          return MaterialUI.greyDarken4
      } else {
          return MaterialUI.greyLighten4
      }
    }
  }
  static var text: UIColor {
    return UIColor { (traitCollection: UITraitCollection) -> UIColor in
      if traitCollection.userInterfaceStyle == .dark {
          return MaterialUI.greyLighten4
      } else {
          return MaterialUI.greyDarken4
      }
    }
  }
  static var lightText: UIColor {
    return UIColor { (traitCollection: UITraitCollection) -> UIColor in
      if traitCollection.userInterfaceStyle == .dark {
          return MaterialUI.greyLighten1
      } else {
          return MaterialUI.greyDarken1
      }
    }
  }
}
