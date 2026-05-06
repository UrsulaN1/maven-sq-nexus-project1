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
# Kernel Tweaks

sysctl -w vm.max_map_count=262144
sysctl -w fs.file-max=65536
ulimit -n 65536
ulimit -u 4096

apt update -y
apt install -y openjdk-17-jdk unzip
cd /opt
wget [https://binaries.sonarsource.com/Distribution/sonarqube/sonarqube-9.9.4.87555.zip](https://binaries.sonarsource.com/Distribution/sonarqube/sonarqube-9.9.4.87555.zip)
unzip sonarqube-9.9.4.87555.zip
mv sonarqube-9.9.4.87555 sonarqube

useradd sonar
chown -R sonar:sonar /opt/sonarqube

**Create Systemd Service**
cat <<EOT> /etc/systemd/system/sonar.service
[Unit]
Description=SonarQube service
After=network.target

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
apt update -y
apt install -y openjdk-8-jdk wget
cd /opt
wget [https://download.sonatype.com/nexus/3/latest-unix.tar.gz](https://download.sonatype.com/nexus/3/latest-unix.tar.gz)
tar -xvzf latest-unix.tar.gz
mv nexus-3.* nexus

useradd nexus
chown -R nexus:nexus /opt/nexus
chown -R nexus:nexus /opt/sonatype-work

cat <<EOT> /etc/systemd/system/nexus.service
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
EOT

systemctl daemon-reload
systemctl enable nexus
systemctl start nexus
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

XML
<settings>
  <servers>
    <server>
      <id>nexus-releases</id>
      <username>admin</username>
      <password>YOUR_NEXUS_PASSWORD</password>
    </server>
    <server>
      <id>nexus-snapshots</id>
      <username>admin</username>
      <password>YOUR_NEXUS_PASSWORD</password>
    </server>
  </servers>
</settings>

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
