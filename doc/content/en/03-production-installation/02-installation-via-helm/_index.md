---
title: Installation via Helm
weight: 3
---

StackGres operator and clusters can be installed using [helm](https://helm.sh/) version >= `2.14` but < `3.x`.

## Install Operator

To install the operator use the following command:

```shell
helm install --namespace stackgres --name stackgres-operator \
  --values my-operator-values.yml \
  https://stackgres.io/downloads/stackgres-k8s/latest/helm-operator.tgz
```

### Parameters

You can specify following parameters values:

* `cert.autoapprove`: if set to false disable automatic approve of certificate
 used by the operator. If disabled the operator installation will not complete
 until the certificate is approved by the kubernetes cluster administrator.
 Default is true.
* `prometheus.allowAutobind`: if set to false disable automatic bind to prometheus
 created using the [prometheus operator](https://github.com/coreos/prometheus-operator).
 If disabled the cluster will not be binded to prometheus when created until done
 manually by the kubernetes cluster administrator.
* `grafana.url`: the URL of the PostgreSQL dashboard created in grafana (required to
 enable the integration of the StackGres UI with grafana)
* `grafana.token`: the grafana API token to access the PostgreSQL dashboard created
 in grafana (required to enable the integration of the StackGres UI with grafana)
* `grafana.httpHost`: the service host name to access grafana (required to enable
 the integration of the StackGres UI with grafana)
* `grafana.schema`: the schema to access grafana. By default http.

## Create a Cluster

To install the operator use the following command:

```shell
helm install --dep-up --namespace my-namespace --name my-cluster \
  --values my-cluster-values.yml \
  https://stackgres.io/downloads/stackgres-k8s/latest/helm-cluster.tgz
```

### Parameters

You can specify following parameters values:

* `config.create`: If true create configuration CRs.
* `config.postgresql.version`: The PostgreSQL versio to use.
* `profiles.create`: If true creates the default profiles.
* `cluster.create`: If true create the cluster (useful to just create configurations).
* `cluster.instances`: The number of instances in the cluster.
* `cluster.pgconfig`: The PostgreSQL configuration CR name.
* `cluster.poolingconfig`: The PgBouncer configuration CR name.
* `cluster.profile`: The profile name used to create cluster's Pods.
* `cluster.restoreconfig`: The restore configuration CR name. Is not enabled by default, so if you want to create a cluster from a existent backup, please see [the restore configuration options](####restore)
* `cluster.backupconfig`: The backup configuration CR name.
* `cluster.volumeSize`: The size set in the persistent volume claim of PostgreSQL data.
* `cluster.storageclass`: The storage class used for the persisitent volume claim of PostgreSQL data.
 If defined, storageClassName: <storageClass>. If set to "-", storageClassName: "", which disables dynamic provisioning.
 If undefined (the default) or set to null, no storageClassName spec is set, choosing the default provisioner.
 (gp2 on AWS, standard on GKE, AWS & OpenStack).
 
#### Backups

By default the chart create a storage class backed by an MinIO server and associate it to the
 `config.backup.volumeWriteManyStorageClass` parameter. To avoid the creation of the MinIO server
 set `config.backup.minio.create` to `false` and specify the `config.backup.volumeWriteManyStorageClass`
 or fill any of the `config.backup.s3`, `config.backup.gcs` or `config.backup.azureblob` sections.
 
* `config.backup.create`: If true create and set the backup configuration for the cluster.
* `config.backup.retention`: Retains specified number of full backups. Default is 5.
* `config.backup.fullSchedule`: Specify when to perform full backups using cron syntax:
 <minute: 0 to 59, or *> <hour: 0 to 23, or * for any value. All times UTC> <day of the month: 1 to 31, or *>
 <month: 1 to 12, or *> <day of the week: 0 to 7 (0 and 7 both represent Sunday), or *>.
 If not specified full backups will be performed each day at 05:00 UTC.
* `config.backup.fullWindow`: Specify the time window in minutes where a full backup will start happening after the point in
 time specified by fullSchedule. If for some reason the system is not capable to start the full backup it will be skipped.
 If not specified the window will be of 1 hour.
* `config.backup.compressionMethod`: To configure compression method used for backups. Possible options are: lz4, lzma, brotli.
 Default method is lz4. LZ4 is the fastest method, but compression ratio is bad. LZMA is way much slower, however it compresses
 backups about 6 times better than LZ4. Brotli is a good trade-off between speed and compression ratio which is about 3 times
 better than LZ4.
* `config.backup.uploadDiskConcurrency`: To configure how many concurrency streams are reading disk during uploads. By default 1 stream.
* `config.backup.tarSizeThreshold`: To configure the size of one backup bundle (in bytes). Smaller size causes granularity and more optimal,
 faster recovering. It also increases the number of storage requests, so it can costs you much money. Default size is 1 GB (1 << 30 - 1 bytes).
* `config.backup.networkRateLimit`: To configure the network upload rate limit during uploads in bytes per second.
* `config.backup.diskRateLimit`: To configure disk read rate limit during uploads in bytes per second.
* `config.backup.pgpConfiguration.name`: The name of the secret with the private key of the OpenPGP configuration for encryption and decryption backups.
* `config.backup.pgpConfiguration.key`: The key in the secret with the private key of the OpenPGP configuration for encryption and decryption backups.
* `config.backup.nfs.create`: If true create a storage class backed by an NFS server that will be used to store backups.

##### PGP Configuration
If you want to use OpenPGP to encrypy your backups, you need to specify pgp configuration to encrypt them. 

* `config.backup.pgpSecret`: By default false. If is set to true, it will enable the use of OpenPGP encrypt your backups
* `config.backup.pgpConfiguration.key`: The key of the secret to select from. Must be a valid secret key.
* `config.backup.pgpConfiguration.name`: Name of the referent. More info: https://kubernetes.io/docs/concepts/overview/working-with-objects/names/#names


##### Volume Source

* `config.backup.volumeSize`: Define the size of the volume used for backups.
* `config.backup.volumeWriteManyStorageClass`: Define the storage class name that will be used to store backups. Must support ReadWriteMany mode access mode.
 If defined, storageClassName: <volumeWriteManyStorageClass>. If set to "-", storageClassName: "", which disables dynamic provisioning.
 If undefined (the default) or set to null, no storageClassName spec is set, choosing the default provisioner.
 (gp2 on AWS, standard on GKE, AWS & OpenStack).

##### Amazon Web Services S3

* `config.backup.s3.prefix`: The AWS S3 bucket and prefix (eg. s3://bucket/path/to/folder).
* `config.backup.s3.accessKey.name`: The name of secret with the access key credentials to access AWS S3 for writing and reading.
* `config.backup.s3.accessKey.key`: The key in the secret with the access key credentials to access AWS S3 for writing and reading.
* `config.backup.s3.secretKey.name`: The name of secret with the secret key credentials to access AWS S3 for writing and reading.
* `config.backup.s3.secretKey.key`: The key in the secret with the secret key credentials to access AWS S3 for writing and reading.
* `config.backup.s3.region`: The AWS S3 region. Region can be detected using s3:GetBucketLocation, but if you wish to avoid this API call
 or forbid it from the applicable IAM policy, specify this property.
* `config.backup.s3.endpoint`: Overrides the default hostname to connect to an S3-compatible service. i.e, http://s3-like-service:9000.
* `config.backup.s3.forcePathStyle`: To enable path-style addressing(i.e., http://s3.amazonaws.com/BUCKET/KEY) when connecting to an S3-compatible
 service that lack of support for sub-domain style bucket URLs (i.e., http://BUCKET.s3.amazonaws.com/KEY). Defaults to false.
* `config.backup.s3.storageClass`: By default, the "STANDARD" storage class is used. Other supported values include "STANDARD_IA"
 for Infrequent Access and "REDUCED_REDUNDANCY" for Reduced Redundancy.
* `config.backup.s3.sse`: To enable S3 server-side encryption, set to the algorithm to use when storing the objects in S3 (i.e., AES256, aws:kms).
* `config.backup.s3.sseKmsId`: If using S3 server-side encryption with aws:kms, the KMS Key ID to use for object encryption.
* `config.backup.s3.cseKmsId`: To configure AWS KMS key for client side encryption and decryption. By default, no encryption is used.
 (region or cseKmsRegion required to be set when using AWS KMS key client side encryption).
* `config.backup.s3.cseKmsRegion`: To configure AWS KMS key region for client side encryption and decryption (i.e., eu-west-1).

##### Google Cloud Storage

* `config.backup.gcs.prefix`: Specify where to store backups (eg. gs://x4m-test-bucket/walg-folder).
* `config.backup.gcs.serviceAccountJsonKey.name`: The name of secret with service account json key to access GCS for writing and reading.
* `config.backup.gcs.serviceAccountJsonKey.key`: The key in the secret with service account json key to access GCS for writing and reading.

##### Azure Blob Storage

* `config.backup.azureblob.prefix`: Specify where to store backups in Azure storage (eg. azure://test-container/walg-folder).
* `config.backup.azureblob.account.name`: The name of secret with storage account name to access Azure Blob Storage for writing and reading.
* `config.backup.azureblob.account.key`: The key in the secret with storage account name to access Azure Blob Storage for writing and reading.
* `config.backup.azureblob.accessKey.name`: The name of secret with the primary or secondary access key for the storage account
 to access Azure Blob Storage for writing and reading.
* `config.backup.azureblob.accessKey.key`: The key in the secret with the primary or secondary access key for the storage account
 to access Azure Blob Storage for writing and reading.
* `config.backup.azureblob.bufferSize`: Overrides the default upload buffer size of 67108864 bytes (64 MB). Note that the size of the buffer
 must be specified in bytes. Therefore, to use 32 MB sized buffers, this variable should be set to 33554432 bytes.
* `config.backup.azureblob.maxBuffers`: Overrides the default maximum number of upload buffers. By default, at most 3 buffers are used concurrently.

#### Restore configuration

By default, stackgres are creates as an empty database. To create a cluster with data from an existent backup, we have the restore configuration. 

* `config.restore.create`: Be default false.  If is set to true it will create a restore configuration CR, and the new cluster will try restore itselfs from an existing backup.
* `config.restore.downloadDiskConcurrency`: By default 1. How many concurrent downloads will attempts during the restoration
* `config.restore.compressionMethod`: By default lz4. Compression method that was used during the backup, could be:  lz4, lzma or brotli.

##### PGP Configuration
If you are using OpenPGP to decrypt your backups, you need to specify pgp configuration to restore them. 

* `config.restore.pgpSecret`: By default false. If is set to true, it will enable the use of OpenPGP to the decrypt the backup
* `config.restore.pgpConfiguration.key`: The key of the secret to select from. Must be a valid secret key.
* `config.restore.pgpConfiguration.name`: Name of the referent. More info: https://kubernetes.io/docs/concepts/overview/working-with-objects/names/#names


##### Restore from a stackgres backup

The easiest way to create a cluster with existent data, is restoring from another stackgres backup. 
 Since every stackgres backup have a CR that represents it, we can use that CR to find out where is stored and the name of the backup to restore. 

* `config.restore.from.backupUID`: The backup CR UID to restore the cluster data
* `config.restore.from.autoCopySecrets`: Default false. If you are creating a cluster in a different namespace than where backup CR is, you might need to copy the secrets where the credentials to access the backup storage to the namespace where you are installing the cluster. If is set to true stackgres will do it automatically. 


##### Restore from an existing database

If it happen that you need to migrating from a existing postgres database to stackgres. Yo can do it with the
 following steps: Fist, use [WAL-G](https://github.com/wal-g/wal-g) to generate a backup from running database and store it in AWS S3, Azureblob or Google Cloud Storage; Second, create secrets to store the credential to access the storage service; Then, specify the storage configuration so stackgres can pull the backup from there. That's it. 

* `config.restore.from.storage.backupName`: Name of the backup to restore. You can use "LATEST" to restore the most recent one. This is only is an storage is specified

###### Restore from Amazon Web Services S3

* `config.restore.from.storage.s3.prefix`: The AWS S3 bucket and prefix (eg. s3://bucket/path/to/folder).
* `config.restore.from.storage.s3.accessKey.name`: The name of secret with the access key credentials to access AWS S3 for writing and reading.
* `config.restore.from.storage.s3.accessKey.key`: The key in the secret with the access key credentials to access AWS S3 for writing and reading.
* `config.restore.from.storage.s3.secretKey.name`: The name of secret with the secret key credentials to access AWS S3 for writing and reading.
* `config.restore.from.storage.s3.secretKey.key`: The key in the secret with the secret key credentials to access AWS S3 for writing and reading.
* `config.restore.from.storage.s3.region`: The AWS S3 region. Region can be detected using s3:GetBucketLocation, but if you wish to avoid this API call
 or forbid it from the applicable IAM policy, specify this property.
* `config.restore.from.storage.s3.endpoint`: Overrides the default hostname to connect to an S3-compatible service. i.e, http://s3-like-service:9000.
* `config.restore.from.storage.s3.forcePathStyle`: To enable path-style addressing(i.e., http://s3.amazonaws.com/BUCKET/KEY) when connecting to an S3-compatible
 service that lack of support for sub-domain style bucket URLs (i.e., http://BUCKET.s3.amazonaws.com/KEY). Defaults to false.
* `config.restore.from.storage.s3.storageClass`: By default, the "STANDARD" storage class is used. Other supported values include "STANDARD_IA"
 for Infrequent Access and "REDUCED_REDUNDANCY" for Reduced Redundancy.
* `config.restore.from.storage.s3.sse`: To enable S3 server-side encryption, set to the algorithm to use when storing the objects in S3 (i.e., AES256, aws:kms).
* `config.restore.from.storage.s3.sseKmsId`: If using S3 server-side encryption with aws:kms, the KMS Key ID to use for object encryption.
* `config.restore.from.storage.s3.cseKmsId`: To configure AWS KMS key for client side encryption and decryption. By default, no encryption is used.
 (region or cseKmsRegion required to be set when using AWS KMS key client side encryption).
* `config.restore.from.storage.s3.cseKmsRegion`: To configure AWS KMS key region for client side encryption and decryption (i.e., eu-west-1).

###### Google Cloud Storage

* `config.restore.from.storage.gcs.prefix`: Specify where to rescover the backup (eg. gs://x4m-test-bucket/walg-folder).
* `config.restore.from.storage.gcs.serviceAccountJsonKey.name`: The name of secret with service account json key to access GCS for writing and reading.
* `config.restore.from.storage.gcs.serviceAccountJsonKey.key`: The key in the secret with service account json key to access GCS for writing and reading.

###### Azure Blob Storage

* `config.backup.azureblob.prefix`: Specify where to recover the backup in Azure storage (eg. azure://test-container/
 walg-folder).
* `config.backup.azureblob.account.name`: The name of secret with storage account name to access Azure Blob Storage
 for writing and reading.
* `config.backup.azureblob.account.key`: The key in the secret with storage account name to access Azure Blob Storage
 for writing and reading.
* `config.backup.azureblob.accessKey.name`: The name of secret with the primary or secondary access key for the
 storage account to access Azure Blob Storage for writing and reading.
* `config.backup.azureblob.accessKey.key`: The key in the secret with the primary or secondary access key for the storage account

#### Sidecars

* `sidecar.pooling`: If true enables connection pooling sidecar.
* `sidecar.util`: If true enables util sidecar.
* `sidecar.prometheus.create`: If true enables prometheus exporter sidecar.
* `sidecar.prometheus.allowAutobind`: If true allow autobind prometheus exporter to the available prometheus
 installed using the [prometheus-operator](https://github.com/coreos/prometheus-operator) by creating required
 `ServiceMonitor` custom resources.