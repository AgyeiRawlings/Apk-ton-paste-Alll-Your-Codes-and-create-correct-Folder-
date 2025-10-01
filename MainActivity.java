package com.example.projectorganizer;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.os.AsyncTask;
import android.view.View;
import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    
    private EditText etCode;
    private EditText etAppName;
    private EditText etPackageName;
    private Button btnGenerate;
    private TextView tvStatus;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        etCode = findViewById(R.id.etCode);
        etAppName = findViewById(R.id.etAppName);
        etPackageName = findViewById(R.id.etPackageName);
        btnGenerate = findViewById(R.id.btnGenerate);
        tvStatus = findViewById(R.id.tvStatus);
        
        btnGenerate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                generateProject();
            }
        });
    }
    
    private void generateProject() {
        String code = etCode.getText().toString().trim();
        String appName = etAppName.getText().toString().trim();
        String packageName = etPackageName.getText().toString().trim();
        
        if (code.isEmpty() || appName.isEmpty() || packageName.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }
        
        new ProjectGeneratorTask().execute(code, appName, packageName);
    }
    
    private class ProjectGeneratorTask extends AsyncTask<String, String, Boolean> {
        
        @Override
        protected void onPreExecute() {
            btnGenerate.setEnabled(false);
            tvStatus.setText("Generating project...");
        }
        
        @Override
        protected Boolean doInBackground(String... params) {
            String code = params[0];
            String appName = params[1];
            String packageName = params[2];
            
            try {
                File projectDir = new File(Environment.getExternalStorageDirectory(), appName);
                
                publishProgress("Creating project structure...");
                createAndroidStructure(projectDir, appName, packageName);
                
                publishProgress("Analyzing code...");
                analyzeAndOrganizeCode(projectDir, code, packageName);
                
                publishProgress("Creating Gradle files...");
                createGradleFiles(projectDir, appName, packageName);
                
                publishProgress("Creating AndroidManifest.xml...");
                createManifest(projectDir, appName, packageName);
                
                publishProgress("Creating default resources...");
                createDefaultResources(projectDir);
                
                return true;
                
            } catch (Exception e) {
                publishProgress("Error: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }
        
        @Override
        protected void onProgressUpdate(String... values) {
            tvStatus.setText(values[0]);
        }
        
        @Override
        protected void onPostExecute(Boolean success) {
            btnGenerate.setEnabled(true);
            if (success) {
                tvStatus.setText("✓ Project created successfully!\n\nLocation: " + 
                    Environment.getExternalStorageDirectory() + "/" + etAppName.getText() + 
                    "\n\nYou can now open it in Android Studio!");
                Toast.makeText(MainActivity.this, "Project ready!", Toast.LENGTH_LONG).show();
            } else {
                tvStatus.setText("Failed to create project. Check logs.");
                Toast.makeText(MainActivity.this, "Failed!", Toast.LENGTH_SHORT).show();
            }
        }
        
        private void analyzeAndOrganizeCode(File projectDir, String code, String pkg) throws IOException {
            // Split code into sections
            String[] sections = code.split("=====|-----");
            
            for (String section : sections) {
                section = section.trim();
                if (section.isEmpty()) continue;
                
                // Detect file type
                if (section.contains("package ") && section.contains("class ")) {
                    // Java file
                    saveJavaFile(projectDir, section, pkg);
                } else if (section.contains("<?xml") && section.contains("<manifest")) {
                    // AndroidManifest
                    saveManifestFile(projectDir, section);
                } else if (section.contains("<?xml") && section.contains("<LinearLayout") || 
                           section.contains("<RelativeLayout") || section.contains("<ConstraintLayout")) {
                    // Layout XML
                    saveLayoutFile(projectDir, section);
                } else if (section.contains("<?xml") && section.contains("<resources>")) {
                    // Resources XML (strings, colors, styles)
                    saveResourceFile(projectDir, section);
                } else if (section.contains("plugins {") || section.contains("android {")) {
                    // Gradle file
                    saveGradleFile(projectDir, section);
                }
            }
        }
        
        private void saveJavaFile(File projectDir, String content, String pkg) throws IOException {
            // Extract class name
            Pattern pattern = Pattern.compile("class\\s+(\\w+)");
            Matcher matcher = pattern.matcher(content);
            String className = "MainActivity";
            if (matcher.find()) {
                className = matcher.group(1);
            }
            
            // Update package name if needed
            content = content.replaceAll("package\\s+[^;]+;", "package " + pkg + ";");
            
            File javaDir = new File(projectDir, "app/src/main/java/" + pkg.replace(".", "/"));
            javaDir.mkdirs();
            
            File javaFile = new File(javaDir, className + ".java");
            writeFile(javaFile, content);
        }
        
        private void saveManifestFile(File projectDir, String content) throws IOException {
            File manifestFile = new File(projectDir, "app/src/main/AndroidManifest.xml");
            writeFile(manifestFile, content);
        }
        
        private void saveLayoutFile(File projectDir, String content) throws IOException {
            File layoutDir = new File(projectDir, "app/src/main/res/layout");
            layoutDir.mkdirs();
            
            // Try to extract layout name from comment or use default
            String layoutName = "activity_main.xml";
            if (content.contains("<!-- ") && content.contains(".xml")) {
                Pattern pattern = Pattern.compile("<!--\\s*([\\w_]+\\.xml)");
                Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    layoutName = matcher.group(1);
                }
            }
            
            File layoutFile = new File(layoutDir, layoutName);
            writeFile(layoutFile, content);
        }
        
        private void saveResourceFile(File projectDir, String content) throws IOException {
            File valuesDir = new File(projectDir, "app/src/main/res/values");
            valuesDir.mkdirs();
            
            String fileName = "styles.xml";
            if (content.contains("<string")) {
                fileName = "strings.xml";
            } else if (content.contains("<color")) {
                fileName = "colors.xml";
            } else if (content.contains("<style")) {
                fileName = "styles.xml";
            }
            
            File resourceFile = new File(valuesDir, fileName);
            writeFile(resourceFile, content);
        }
        
        private void saveGradleFile(File projectDir, String content) throws IOException {
            File gradleFile;
            if (content.contains("buildscript")) {
                gradleFile = new File(projectDir, "build.gradle");
            } else {
                gradleFile = new File(projectDir, "app/build.gradle");
            }
            writeFile(gradleFile, content);
        }
        
        private void createAndroidStructure(File base, String appName, String pkg) {
            String[] dirs = {
                "app/src/main/java/" + pkg.replace(".", "/"),
                "app/src/main/res/layout",
                "app/src/main/res/values",
                "app/src/main/res/drawable",
                "app/src/main/res/mipmap-hdpi",
                "app/src/main/res/mipmap-mdpi",
                "app/src/main/res/mipmap-xhdpi",
                "app/src/main/res/mipmap-xxhdpi",
                "app/src/main/assets",
                "app/build",
                "app/libs",
                "gradle/wrapper"
            };
            
            for (String dir : dirs) {
                new File(base, dir).mkdirs();
            }
        }
        
        private void createGradleFiles(File base, String appName, String pkg) throws IOException {
            // Check if gradle files already exist from pasted code
            File appGradle = new File(base, "app/build.gradle");
            if (appGradle.exists()) return;
            
            String rootGradle = "buildscript {\n" +
                "    repositories {\n" +
                "        google()\n" +
                "        mavenCentral()\n" +
                "    }\n" +
                "    dependencies {\n" +
                "        classpath 'com.android.tools.build:gradle:7.4.0'\n" +
                "    }\n" +
                "}\n\n" +
                "allprojects {\n" +
                "    repositories {\n" +
                "        google()\n" +
                "        mavenCentral()\n" +
                "    }\n" +
                "}";
            
            writeFile(new File(base, "build.gradle"), rootGradle);
            
            String appGradleContent = "plugins {\n" +
                "    id 'com.android.application'\n" +
                "}\n\n" +
                "android {\n" +
                "    namespace '" + pkg + "'\n" +
                "    compileSdk 33\n\n" +
                "    defaultConfig {\n" +
                "        applicationId \"" + pkg + "\"\n" +
                "        minSdk 21\n" +
                "        targetSdk 33\n" +
                "        versionCode 1\n" +
                "        versionName \"1.0\"\n" +
                "    }\n\n" +
                "    buildTypes {\n" +
                "        release {\n" +
                "            minifyEnabled false\n" +
                "        }\n" +
                "    }\n" +
                "}\n\n" +
                "dependencies {\n" +
                "}";
            
            writeFile(new File(base, "app/build.gradle"), appGradleContent);
            
            writeFile(new File(base, "settings.gradle"), 
                "rootProject.name = '" + appName + "'\ninclude ':app'");
            
            writeFile(new File(base, "gradle.properties"),
                "org.gradle.jvmargs=-Xmx2048m\nandroid.useAndroidX=true");
        }
        
        private void createManifest(File base, String appName, String pkg) throws IOException {
            File manifestFile = new File(base, "app/src/main/AndroidManifest.xml");
            if (manifestFile.exists()) return;
            
            String manifest = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    package=\"" + pkg + "\">\n\n" +
                "    <uses-permission android:name=\"android.permission.INTERNET\" />\n\n" +
                "    <application\n" +
                "        android:allowBackup=\"true\"\n" +
                "        android:icon=\"@mipmap/ic_launcher\"\n" +
                "        android:label=\"" + appName + "\"\n" +
                "        android:theme=\"@android:style/Theme.Material.Light\">\n" +
                "        <activity\n" +
                "            android:name=\".MainActivity\"\n" +
                "            android:exported=\"true\">\n" +
                "            <intent-filter>\n" +
                "                <action android:name=\"android.intent.action.MAIN\" />\n" +
                "                <category android:name=\"android.intent.category.LAUNCHER\" />\n" +
                "            </intent-filter>\n" +
                "        </activity>\n" +
                "    </application>\n\n" +
                "</manifest>";
            
            writeFile(manifestFile, manifest);
        }
        
        private void createDefaultResources(File base) throws IOException {
            // Create default strings.xml if not exists
            File stringsFile = new File(base, "app/src/main/res/values/strings.xml");
            if (!stringsFile.exists()) {
                String strings = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<resources>\n" +
                    "    <string name=\"app_name\">MyApp</string>\n" +
                    "</resources>";
                writeFile(stringsFile, strings);
            }
            
            // Create default styles.xml if not exists
            File stylesFile = new File(base, "app/src/main/res/values/styles.xml");
            if (!stylesFile.exists()) {
                String styles = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<resources>\n" +
                    "    <style name=\"AppTheme\" parent=\"android:Theme.Material.Light\">\n" +
                    "    </style>\n" +
                    "</resources>";
                writeFile(stylesFile, styles);
            }
        }
        
        private void writeFile(File file, String content) throws IOException {
            file.getParentFile().mkdirs();
            FileWriter writer = new FileWriter(file);
            writer.write(content);
            writer.close();
        }
    }
}
