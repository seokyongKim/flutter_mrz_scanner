#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html.
#
Pod::Spec.new do |s|
  s.name             = 'flutter_mrz_scanner_enhanced'
  s.version          = '0.0.1'
  s.summary          = 'Flutter plugin for scanning MRZ codes from ID documents.'
  s.description      = <<-DESC
A Flutter plugin for scanning MRZ (Machine Readable Zone) codes from identity documents, passports, and travel documents.
                       DESC
  s.homepage         = 'https://github.com/ELMEHDAOUIAhmed/flutter_mrz_scanner_enhanced'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'ELMEHDAOUIAhmed' => 'email@example.com' }
  s.source           = { :path => '.' }
  s.source_files = 'Classes/**/*'
  s.resources = ['Assets/TraineedDataBundle.bundle']
  s.dependency 'Flutter'
  s.dependency 'SwiftyTesseract', '~> 3.1.3'
  s.platform = :ios, '12.0'
  
  # Flutter.framework does not contain a i386 slice.
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES', 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386' }
  s.swift_version = '5.0'
end 