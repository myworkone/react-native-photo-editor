//
//  PhotoEditor.swift
//  PhotoEditor
//
//  Created by Donquijote on 27/07/2021.
//

import Foundation
import UIKit
import Photos
import SDWebImage
import AVFoundation
import ZLImageEditor

public enum ImageLoad: Error {
    case failedToLoadImage(String)
}

@objc(PhotoEditor)
class PhotoEditor: NSObject {
    var window: UIWindow?
    var bridge: RCTBridge!
    
    var resolve: RCTPromiseResolveBlock!
    var reject: RCTPromiseRejectBlock!
    
    @objc(open:withResolver:withRejecter:)
    func open(options: NSDictionary, resolve:@escaping RCTPromiseResolveBlock,reject:@escaping RCTPromiseRejectBlock) -> Void {
        
        // handle path
        guard let path = options["path"] as? String else {
            reject("DONT_FIND_IMAGE", "Dont find image", nil)
            return;
        }
        
        getUIImage(url: path) { image in
            DispatchQueue.main.async {
                //  set config
                self.setConfiguration(options: options, resolve: resolve, reject: reject)
                self.presentController(image: image)
            }
        } reject: {_ in
            reject("LOAD_IMAGE_FAILED", "Load image failed: " + path, nil)
        }
    }
    
    func onCancel() {
        self.reject("USER_CANCELLED", "User has cancelled", nil)
    }
    
    private func setConfiguration(options: NSDictionary, resolve:@escaping RCTPromiseResolveBlock,reject:@escaping RCTPromiseRejectBlock) -> Void{
        self.resolve = resolve;
        self.reject = reject;
        
        // Stickers
        let stickers = options["stickers"] as? [String] ?? []
        ZLImageEditorConfiguration.default().imageStickerContainerView = StickerView(stickers: stickers)
        ZLImageEditorConfiguration.default().clipRatios(ZLImageClipRatio.all)
        ZLImageEditorConfiguration.default().tools.removeAll { $0 == .filter }
        ZLImageEditorConfiguration.default().tools.removeAll { $0 == .mosaic }
        ZLImageEditorConfiguration.default().tools.removeAll { $0 == .adjust }
        
        
        // //Config
        // ZLImageEditorConfiguration.default().editDoneBtnBgColor = UIColor(red:255/255.0, green:238/255.0, blue:101/255.0, alpha:1.0)

        // ZLImageEditorConfiguration.default().editImageTools = [.draw, .clip, .filter, .imageSticker, .textSticker]
        
        //Filters Lut
        // do {
        //     let filters = ColorCubeLoader()
        //     ZLImageEditorConfiguration.default().filters = try filters.load()
        // } catch {
        //     assertionFailure("\(error)")
        // }
    }
    
  private func presentController(image: UIImage) {
      if let topController = UIApplication.getTopViewController() { // Renamed 'controller' to 'topController' for clarity
          topController.modalTransitionStyle = .crossDissolve

          ZLEditImageViewController.showEditImageVC(
              parentVC: topController, // Corrected variable name
              image: image,
              completion: { [weak self] (editedImage, editModel) in // This is the 'completion' block
                  guard let self = self else { return } // Ensure self is not nil

                  // We expect 'editedImage' to be non-nil on success
                  let documentsPath = NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true)[0] as String
                  let fileName = String(Int64(Date().timeIntervalSince1970 * 1000)) + ".png"
                  let destinationPath = URL(fileURLWithPath: documentsPath).appendingPathComponent(fileName)

                  do {
                      try editedImage.pngData()?.write(to: destinationPath)
                      self.resolve(destinationPath.absoluteString)
                  } catch {
                      debugPrint("Writing file error: ", error)
                      self.reject("SAVE_ERROR", "Failed to save edited image.", error)
                  }
              },
              cancelBlock: { [weak self] in // This is the 'cancelBlock'
                  guard let self = self else { return }
                  // Call the onCancel method you already have, or directly reject
                  self.onCancel() // This will call self.reject("USER_CANCELLED", ...)
              }
          )
      } else {
          // Handle the case where topViewController is nil, perhaps reject the promise
          self.reject("NO_TOP_VC", "Could not find a top view controller to present the editor.", nil)
      }
  }
    
    
    private func getUIImage (url: String ,completion:@escaping (UIImage) -> (), reject:@escaping(String)->()){
        if let path = URL(string: url) {
            SDWebImageManager.shared.loadImage(with: path, options: .continueInBackground, progress: { (recieved, expected, nil) in
            }, completed: { (downloadedImage, data, error, SDImageCacheType, true, imageUrlString) in
                DispatchQueue.main.async {
                    if(error != nil){
                        print("error", error as Any)
                        reject("false")
                        return;
                    }
                    if downloadedImage != nil{
                        completion(downloadedImage!)
                    }
                }
            })
        }else{
            reject("false")
        }
    }
    
}

extension UIApplication {
    class func getTopViewController(base: UIViewController? = UIApplication.shared.keyWindow?.rootViewController) -> UIViewController? {
        
        if let nav = base as? UINavigationController {
            return getTopViewController(base: nav.visibleViewController)
        } else if let tab = base as? UITabBarController, let selected = tab.selectedViewController {
            return getTopViewController(base: selected)
        } else if let presented = base?.presentedViewController {
            return getTopViewController(base: presented)
        }
        
        return base
    }
}
