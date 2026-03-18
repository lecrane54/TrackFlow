Pod::Spec.new do |spec|
    spec.name                     = 'trackflow_provider_amplitude_ios'
    spec.version                  = '1.0.0'
    spec.homepage                 = 'https://github.com/lecrane54/TrackFlow'
    spec.source                   = { :http=> ''}
    spec.authors                  = ''
    spec.license                  = ''
    spec.summary                  = 'TrackFlow Amplitude iOS Provider'
    spec.vendored_frameworks      = 'build/cocoapods/framework/trackflow_provider_amplitude_ios.framework'
    spec.libraries                = 'c++'
    spec.ios.deployment_target    = '15.0'
    spec.dependency 'Amplitude', '~> 8.0'
    if !Dir.exist?('build/cocoapods/framework/trackflow_provider_amplitude_ios.framework') || Dir.empty?('build/cocoapods/framework/trackflow_provider_amplitude_ios.framework')
        raise "
        Kotlin framework 'trackflow_provider_amplitude_ios' doesn't exist yet, so a proper Xcode project can't be generated.
        'pod install' should be executed after running ':generateDummyFramework' Gradle task:
            ./gradlew :trackflow-provider-amplitude-ios:generateDummyFramework
        Alternatively, proper pod installation is performed during Gradle sync in the IDE (if Podfile location is set)"
    end
    spec.xcconfig = {
        'ENABLE_USER_SCRIPT_SANDBOXING' => 'NO',
    }
    spec.pod_target_xcconfig = {
        'KOTLIN_PROJECT_PATH' => ':trackflow-provider-amplitude-ios',
        'PRODUCT_MODULE_NAME' => 'trackflow_provider_amplitude_ios',
    }
    spec.script_phases = [
        {
            :name => 'Build trackflow_provider_amplitude_ios',
            :execution_position => :before_compile,
            :shell_path => '/bin/sh',
            :script => <<-SCRIPT
                if [ "YES" = "$OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED" ]; then
                    echo "Skipping Gradle build task invocation due to OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED environment variable set to \"YES\""
                    exit 0
                fi
                set -ev
                REPO_ROOT="$PODS_TARGET_SRCROOT"
                "$REPO_ROOT/../gradlew" -p "$REPO_ROOT" $KOTLIN_PROJECT_PATH:syncFramework \
                    -Pkotlin.native.cocoapods.platform=$PLATFORM_NAME \
                    -Pkotlin.native.cocoapods.archs="$ARCHS" \
                    -Pkotlin.native.cocoapods.configuration="$CONFIGURATION"
            SCRIPT
        }
    ]
end
