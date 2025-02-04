package com.capstone.navicamp

import android.content.Context
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import java.io.InputStream
import java.util.Date
import java.util.Properties

object AwsUtils {

    private lateinit var context: Context

    fun initialize(context: Context) {
        this.context = context
    }

    private fun loadAwsCredentials(): BasicAWSCredentials {
        val props = Properties()
        val assetManager = context.assets
        val inputStream: InputStream = assetManager.open("config.properties")
        props.load(inputStream)
        val accessKey = props.getProperty("aws.accessKeyId")
        val secretKey = props.getProperty("aws.secretKey")
        return BasicAWSCredentials(accessKey, secretKey)
    }

    public val s3Client: AmazonS3Client by lazy {
        val credentials = loadAwsCredentials()
        AmazonS3Client(credentials).apply {
            setRegion(com.amazonaws.regions.Region.getRegion("ap-southeast-1"))
        }
    }

    fun generatePresignedUrl(bucketName: String, objectKey: String): String {
        val expiration = Date(System.currentTimeMillis() + 3600000) // 1 hour expiration
        val generatePresignedUrlRequest = GeneratePresignedUrlRequest(bucketName, objectKey)
            .withMethod(com.amazonaws.HttpMethod.GET)
            .withExpiration(expiration)
        return s3Client.generatePresignedUrl(generatePresignedUrlRequest).toString()
    }
}