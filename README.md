# NaviCamp-new
 This project will serve as an assistance request and assistance responder mobile application to be used for locomotor disabled people or safety officers inside our campus, MMCL.

# IMPORTANT PLEASE SEE

**STEP 1:** inside the project files of android studio then go to app/src/main/assets (if assets folder does not exist make one)

![image](https://github.com/user-attachments/assets/fbad2fcc-82c1-4faf-8b03-db6a95ba2687)

**STEP 2:** make a new file inside of assets folder named config.properties

![image](https://github.com/user-attachments/assets/6d0fed1a-1f43-43c1-84bb-b53c96007351)

**STEP 3:**
inside of config.properties place the aws access key and secret access key then save it

![image](https://github.com/user-attachments/assets/7878ce0e-a828-4eef-8a1b-d8566f5dbffb)

```
aws.accessKeyId=AKIAQUFLQAKB4BQ6MX4Q
aws.secretKey=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
```
**STEP 4:** go to github desktop > repository tab > show in explorer

![image](https://github.com/user-attachments/assets/1777cb57-ce26-4a5b-9711-ef93bffac4ee)

**STEP 5:** open ".gitignore" file using text or code editor then the code should be like this

![image](https://github.com/user-attachments/assets/e3a732c9-c2f9-4a7a-825d-99358c715bbb)

```
*.iml
.gradle
/local.properties
/.idea/caches
/.idea/libraries
/.idea/modules.xml
/.idea/workspace.xml
/.idea/navEditor.xml
/.idea/assetWizardSettings.xml
.DS_Store
/build
/captures
.externalNativeBuild
.cxx
local.properties

# Ignore config.properties file
app/src/main/assets/config.properties
```


