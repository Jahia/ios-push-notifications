# ios-push-notifications

This project contains a module that allows Jahia to support push notifications for iOS. It contains in a single package
everything that is needed server-side to send push notifications to mobile users

## Requirements
- An iOS developer account
- A push notification certificate associated with an unique application identifier

## Setup

1. Generate the production and development certificates for your application in the iTunes Member Center provisioning
center.
2. Download the certificates and double click on them to import them into the Keychain Access utility
3. Locate the certificates in the Keychain Access utility and right click one to export it. The default export format
should be .p12 which is want we want. You will be prompted for a password, make sure you remember it as you will need
it for step 4
4. Copy the generate .p12 files into the project in the src/main/resources directory, and edit the
src/main/resources/META-INF/spring/ios-push-notifications.xml to update the path and password to point to your
certificates.

TODO

## End point for mobile clients

N/A

## TODO

- Add an endpoint to get the mobile's token
- Add a system to store the token either in a cookie or in the user's properties if he is logged into Jahia
