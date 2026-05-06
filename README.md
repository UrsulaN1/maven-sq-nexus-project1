# Maven-SonarQube-Nexus CI/CD Infrastructure

This repository provides automation scripts and configuration guidelines for setting up a robust CI/CD environment. It includes User Data scripts for provisioning Maven Build Servers, SonarQube Code Quality Analysis, and Nexus Repository Manager on both Ubuntu and Amazon Linux 2023.

---

## 🚀 Quick Start

To clone the project repository:

```bash
git clone -b maven-sonarqube-nexus [https://github.com/awanmbandi/realworld-cicd-pipeline-project.git](https://github.com/awanmbandi/realworld-cicd-pipeline-project.git)
```

## 🛠️ Infrastructure Provisioning (User Data Scripts)

### 1. Maven Build Server

Choose the script based on your EC2 instance OS.

```bash
#!/bin/bash
sudo apt update -y
sudo apt install openjdk-17-jdk -y
sudo apt install maven -y
sudo apt install git -y
java -version
mvn -version
```

```bash
#!/bin/bash
dnf update -y
dnf install java-17-amazon-corretto-devel git wget -y

cd /opt
wget [https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz](https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz)
tar -xzvf apache-maven-3.9.6-bin.tar.gz
ln -s /opt/apache-maven-3.9.6 /opt/maven

echo "export M2_HOME=/opt/maven" > /etc/profile.d/maven.sh
echo "export PATH=\\$M2_HOME/bin:\\$PATH" >> /etc/profile.d/maven.sh
source /etc/profile.d/maven.sh
```

### 2. SonarQube Server

Note: SonarQube requires specific system tweaks for Elasticsearch.

```bash
#!/bin/bash
sudo sysctl -w vm.max_map_count=262144
sudo sysctl -w fs.file-max=65536
ulimit -n 65536
ulimit -u 4096

sudo apt update -y
sudo apt install -y openjdk-17-jdk unzip

cd /opt
sudo wget [https://binaries.sonarsource.com/Distribution/sonarqube/sonarqube-9.9.4.87555.zip](https://binaries.sonarsource.com/Distribution/sonarqube/sonarqube-9.9.4.87555.zip)
sudo unzip sonarqube-9.9.4.87555.zip
sudo mv sonarqube-9.9.4.87555 sonarqube

sudo useradd sonar
sudo chown -R sonar:sonar /opt/sonarqube
sudo su - sonar -c "/opt/sonarqube/bin/linux-x86-64/sonar.sh start"
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

### 3. Nexus Repository Manager

```bash
#!/bin/bash
sudo apt update -y
sudo apt install -y openjdk-8-jdk wget

cd /opt
sudo wget [https://download.sonatype.com/nexus/3/latest-unix.tar.gz](https://download.sonatype.com/nexus/3/latest-unix.tar.gz)
sudo tar -xvzf latest-unix.tar.gz
sudo mv nexus-3.* nexus

sudo useradd nexus
sudo chown -R nexus:nexus /opt/nexus
sudo chown -R nexus:nexus /opt/sonatype-work

sudo cat <<EOT > /etc/systemd/system/nexus.service
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

sudo systemctl daemon-reload
sudo systemctl enable nexus
sudo systemctl start nexus
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
```
