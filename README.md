# Maven-SonarQube-Nexus CI/CD Infrastructure

This repository provides automated provisioning scripts and configuration guidelines for setting up a robust, manual-trigger CI/CD environment. This setup is designed for engineers looking to master artifact management and code quality gates.

---

## 🏗️ Architecture Overview

* **Maven Build Server**: Compiles code, runs tests, and packages artifacts.
* **SonarQube**: Performs static code analysis and quality gate checks.
* **Nexus Repository Manager**: Hosts private Maven repositories for snapshots and releases.

---

## 🛠️ Infrastructure Provisioning (User Data Scripts)

### 1. Maven Build Server

Run these scripts via EC2 User Data or manually as root.

#### **Option A: Ubuntu**

```bash
#!/bin/bash
sudo apt update -y
sudo apt install openjdk-17-jdk maven git -y
```

#### **Option B: Amazon Linux 2023**

```bash
#!/bin/bash
dnf update -y
dnf install java-17-amazon-corretto-devel git wget -y
cd /opt
wget [https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz](https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz)
tar -xzvf apache-maven-3.9.6-bin.tar.gz
ln -s /opt/apache-maven-3.9.6 /opt/maven
echo "export M2_HOME=/opt/maven" > /etc/profile.d/maven.sh
echo "export PATH=\$M2_HOME/bin:\$PATH" >> /etc/profile.d/maven.sh
source /etc/profile.d/maven.sh
```

### 2. SonarQube Server

SonarQube requires kernel tweaks for the underlying Elasticsearch engine.

#### **Option A: Ubuntu

```bash
#!/bin/bash
# 1. System Updates & Prerequisites
apt-get update -y
apt-get install -y openjdk-17-jdk unzip wget

# 2. Increase Kernel Limits for Elasticsearch (Crucial for SonarQube)
# This prevents the service from failing on boot
echo "vm.max_map_count=262144" >> /etc/sysctl.conf
echo "fs.file-max=65536" >> /etc/sysctl.conf
sysctl -p

# 3. Create a dedicated sonar user
useradd -m -d /opt/sonarqube -s /bin/bash sonar

# 4. Download and Install SonarQube (Community Edition)
cd /tmp
wget https://binaries.sonarsource.com/Distribution/sonarqube/sonarqube-10.4.1.88267.zip
unzip sonarqube-10.4.1.88267.zip
mv sonarqube-10.4.1.88267/* /opt/sonarqube
chown -R sonar:sonar /opt/sonarqube

# 5. Create Systemd Service File
cat <<EOT > /etc/systemd/system/sonar.service
[Unit]
Description=SonarQube service
After=syslog.target network.target

[Service]
Type=forking
ExecStart=/opt/sonarqube/bin/linux-x86-64/sonar.sh start
ExecStop=/opt/sonarqube/bin/linux-x86-64/sonar.sh stop
User=sonar
Group=sonar
Restart=always
LimitNOFILE=65536
LimitNPROC=4096

[Install]
WantedBy=multi-user.target
EOT

# 6. Enable and Start the Service
systemctl daemon-reload
systemctl enable sonar
systemctl start sonar

```

```bash
#!/bin/bash
dnf update -y
dnf install java-17-amazon-corretto-devel wget unzip -y

sysctl -w vm.max_map_count=262144
sysctl -w fs.file-max=65536
echo "sonarqube - nofile 65536" >> /etc/security/limits.conf
echo "sonarqube - nproc 4096" >> /etc/security/limits.conf

cd /opt
wget [https://binaries.sonarsource.com/Distribution/sonarqube/sonarqube-10.4.1.88267.zip](https://binaries.sonarsource.com/Distribution/sonarqube/sonarqube-10.4.1.88267.zip)
unzip sonarqube-10.4.1.88267.zip
mv sonarqube-10.4.1.88267 sonarqube

useradd sonarqube
chown -R sonarqube:sonarqube /opt/sonarqube
sudo -u sonarqube /opt/sonarqube/bin/linux-x86-64/sonar.sh start
```

### 3. Nexus Repository Manager (Ubuntu)

```bash
#!/bin/bash

# Update package list
sudo apt update -y

# Install OpenJDK 17
sudo apt install openjdk-17-jdk -y

# Verify Java installation
java --version

# Download the latest Nexus tarball to /opt
sudo wget https://download.sonatype.com/nexus/3/nexus-unix-x86-64-3.78.1-02.tar.gz -O /opt/latest-unix.tar.gz

# Extract the tarball
sudo tar -xvzf /opt/latest-unix.tar.gz -C /opt

# Rename the extracted directory to /opt/nexus
# Note: The exact version number may vary; using a wildcard to handle this
sudo mv /opt/nexus-3.* /opt/nexus

# Create a nexus user non-interactively
sudo adduser --disabled-password --gecos "" nexus

# Grant the nexus user sudo privileges without a password
sudo su
echo "nexus ALL=(ALL) NOPASSWD: ALL" | sudo tee -a /etc/sudoers

# Change ownership of nexus directories
sudo chown -R nexus:nexus /opt/nexus
sudo chown -R nexus:nexus /opt/sonatype-work

# Configure the nexus.rc file to run as the nexus user
echo 'run_as_user="nexus"' | sudo tee /opt/nexus/bin/nexus.rc

# Append JVM options to nexus.vmoptions
cat <<EOL | sudo tee -a /opt/nexus/bin/nexus.vmoptions
-XX:MaxDirectMemorySize=2703m
-Djava.net.preferIPv4Stack=true
EOL

# Create the systemd service file for Nexus
cat <<EOL | sudo tee /etc/systemd/system/nexus.service
[Unit]
Description=nexus service
After=network.target

[Service]
Type=forking
LimitNOFILE=65536
ExecStart=/opt/nexus/bin/nexus start
ExecStop=/opt/nexus/bin/nexus stop
User=nexus
Restart=on-abort

[Install]
WantedBy=multi-user.target
EOL

# Reload systemd, start, and enable the Nexus service
sudo systemctl daemon-reload
sudo systemctl start nexus
sudo systemctl enable nexus

# Check the status of the Nexus service
sudo systemctl status nexus

# Allow Nexus default port (8081) through the firewall
sudo su
ufw allow 8081/tcp
```

```bash
#!/bin/bash
dnf update -y
dnf install java-17-amazon-corretto-devel wget -y

cd /opt
wget [https://download.sonatype.com/nexus/3/latest-unix.tar.gz](https://download.sonatype.com/nexus/3/latest-unix.tar.gz)
tar -xzvf latest-unix.tar.gz
mv nexus-3* nexus

useradd nexus
chown -R nexus:nexus /opt/nexus
chown -R nexus:nexus /opt/sonatype-work

sudo -u nexus /opt/nexus/bin/nexus start
```

## 🔐 Post-Installation & Access

| Service | URL | Default Username | Default Password |
| :--- | :--- | :--- | :--- |
| **SonarQube** | `http://<Public_IP>:9000` | `admin` | `admin` (Reset required) |
| **Nexus** | `http://<Public_IP>:8081` | `admin` | *See instructions below* |

Sources
<https://www.howtoforge.com/how-to-install-nexus-repository-manager-on-ubuntu-22-04/>

**To retrieve initial Nexus password:**

```bash
cat /opt/sonatype-work/nexus3/admin.password
```

## Security Group Configuration

**Inbound Rules Matrix**
The following table defines the required inbound traffic rules for SonarQube, Nexus, and Build nodes.

| Tool | Port | Source | Purpose |
| :--- | :--- | :--- | :--- |
| **SonarQube** | 9000 | Maven SG ID | Analysis Upload |
| **SonarQube** | 9000 | My IP | Web Dashboard Access |
| **Nexus** | 8081 | Maven SG ID | Artifact Deployment |
| **Nexus** | 8081 | My IP | Web Dashboard Access |
| **Maven** | 22 | My IP / Bastion | SSH Admin Access |
| **Common** | 22 | My IP | SSH Access for all nodes |

### Configuration Details

**SonarQube**
-**Port 9000**: Used for both the web interface and the API endpoint where Maven build agents upload analysis reports.
-**Access Control**: Ensure the Security Group (SG) associated with the Maven build server is whitelisted.

**Nexus Repository Manager**
-**Port 8081**: The default port for the Nexus web console and the repository manager.
-**Usage**: Facilitates artifact storage and retrieval during the CI/CD pipeline.

**SSH Access**
-**Port 22**: Standard SSH port.
-**Security Best Practice**: Direct access is limited to "My IP," while administrative access to the Maven node is routed through a Bastion host or specific IP range for enhanced security.

**Outbound Rules**
**All Servers**: Allow All Traffic (to download dependencies).

**Maven Server**: Ensure outbound access to SonarQube (9000) and Nexus (8081).

## 🧪 **Connectivity Testing**

Run these commands from your Maven Build Server to verify connections:

```bash
# Test connection to Nexus
nc -zv <NEXUS_PRIVATE_IP> 8081

# Test connection to SonarQube
nc -zv <SONAR_PRIVATE_IP> 9000
`
``
### 🚀 3. Integration & Connectivity

Step A: Configure Maven Settings
On the Maven Build Server, create ~/.m2/settings.xml to allow Maven to authenticate with Nexus:

```bash
mkdir -p ~/.m2
nano ~/.m2/settings.xml
```

### Add the Authentication Block

```bash
XML
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <servers>
    <server>
      <id>nexus-snapshots</id>
      <username>admin</username>
      <password>your_nexus_password</password>
    </server>
    <server>
      <id>nexus-releases</id>
      <username>admin</username>
      <password>your_nexus_password</password>
    </server>
  </servers>
</settings>
```

### Verify the permissions

Since the file contains plain-text passwords, it is a best practice to restrict its permissions so only your user can read it:

```bash
chmod 600 ~/.m2/settings.xml
```

Step B: Connectivity Test
Run from the Maven Server to ensure firewall/SGs are open:

```bash
nc -zv <SONAR_IP> 9000
nc -zv <NEXUS_IP> 8081
```

### 🚀 4. Running the Pipeline Manually

**1. Code Analysis**
Run this from your project root to send data to SonarQube:

```bash
mvn sonar:sonar \
  -Dsonar.projectKey=my-app \
  -Dsonar.host.url=http://<SONAR_IP>:9000 \
  -Dsonar.login=<SONAR_TOKEN>
  ```

**2.Artifact Deployment**
Deploy your .jar or .war files to the Nexus Repository:

```bash
mvn clean deploy
```

**🧹 Cleanup**
To stop services:

```bash
sudo systemctl stop sonar
sudo systemctl stop nexus
```
