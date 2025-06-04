require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "react-native-photo-editor"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => "10.0" }
  s.source       = { :git => "https://github.com/baronha/react-native-photo-editor.git", :tag => "#{s.version}" }
  s.swift_version = ['5.0', '5.1', '5.2']

  s.source_files = "ios/**/*.{h,m,mm,swift}"
  
  s.dependency "React-Core"
  s.dependency "SDWebImage", "~> 5.11.1"
  s.dependency 'SDWebImageWebPCoder', '~> 0.8.4'
  s.dependency 'ZLImageEditor'

 # --- THIS IS THE MAJOR CHANGE ---
  # Remove the entire subspec 'ZLImageEditor' block
  # And add a direct dependency to your ZLImageEditor fork:

  # Option 1: Depend on the main/master branch of your ZLImageEditor fork
  # s.dependency 'ZLImageEditor', :git => 'https://github.com/myworkone/ZLImageEditor.git'

  # Option 2: Depend on a specific tag from your ZLImageEditor fork (RECOMMENDED for stability)
  # Ensure you have created this tag in your myworkone/ZLImageEditor repository.
  # s.dependency 'ZLImageEditor', :git => 'https://github.com/myworkone/ZLImageEditor.git', :tag => 'v2.0.1-myfork' # Example tag

  # Option 3: Depend on a specific branch from your ZLImageEditor fork
  # s.dependency 'ZLImageEditor', :git => 'https://github.com/myworkone/ZLImageEditor.git', :branch => 'AF-3384-photo-editor-ios'
  # --- END OF MAJOR CHANGE ---

  # The frameworks and resources previously in the subspec (UIKit, Accelerate, bundles, pngs)
  # should now be defined within the .podspec of your myworkone/ZLImageEditor fork itself.
  # ZLImageEditor's own podspec will handle its resources and framework dependencies.
  
end
