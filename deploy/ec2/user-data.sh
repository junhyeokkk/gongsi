#!/bin/bash
# EC2 첫 부팅 1회 - Java 21, psql 클라이언트, 1GB 스왑 (t4g.micro 메모리 1GB 보완)
set -euxo pipefail

dnf install -y java-21-amazon-corretto-headless
dnf install -y postgresql16 || dnf install -y postgresql15

if [ ! -f /swapfile ]; then
  dd if=/dev/zero of=/swapfile bs=1M count=1024
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap defaults 0 0' >> /etc/fstab
fi

timedatectl set-timezone Asia/Seoul
