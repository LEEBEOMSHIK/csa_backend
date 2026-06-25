package org.example.csa_backend.config;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class S3MediaStorageClientTest {

    private final S3Client s3Client = mock(S3Client.class);
    private final S3MediaStorageClient client = new S3MediaStorageClient(s3Client, "my-bucket");

    @Test
    void uploadSendsPutObjectWithBucketKeyAndContentType() {
        byte[] data = new byte[]{1, 2, 3};

        client.upload("fairytales/7/page_1.png", data, "image/png");

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

        PutObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo("my-bucket");
        assertThat(request.key()).isEqualTo("fairytales/7/page_1.png");
        assertThat(request.contentType()).isEqualTo("image/png");
    }

    @Test
    void deleteByPrefixListsThenDeletesMatchingObjects() {
        ListObjectsV2Response listing = ListObjectsV2Response.builder()
                .contents(
                        S3Object.builder().key("fairytales/7/page_1.png").build(),
                        S3Object.builder().key("fairytales/7/page_1_dad_ko.mp3").build()
                )
                .build();
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listing);

        client.deleteByPrefix("fairytales/7/");

        ArgumentCaptor<ListObjectsV2Request> listCaptor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2(listCaptor.capture());
        assertThat(listCaptor.getValue().bucket()).isEqualTo("my-bucket");
        assertThat(listCaptor.getValue().prefix()).isEqualTo("fairytales/7/");

        ArgumentCaptor<DeleteObjectsRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(deleteCaptor.capture());
        DeleteObjectsRequest deleteRequest = deleteCaptor.getValue();
        assertThat(deleteRequest.bucket()).isEqualTo("my-bucket");
        assertThat(deleteRequest.delete().objects())
                .extracting("key")
                .containsExactly("fairytales/7/page_1.png", "fairytales/7/page_1_dad_ko.mp3");
    }

    @Test
    void deleteByPrefixSkipsDeleteWhenNoObjectsMatch() {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder().build());

        client.deleteByPrefix("fairytales/99/");

        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    void deleteByPrefixPaginatesAndDeletesInBatchesOfThousand() {
        List<S3Object> firstPageObjects = IntStream.rangeClosed(1, 1000)
                .mapToObj(i -> S3Object.builder().key("fairytales/7/page_" + i + ".png").build())
                .toList();
        ListObjectsV2Response firstPage = ListObjectsV2Response.builder()
                .contents(firstPageObjects)
                .isTruncated(true)
                .nextContinuationToken("next-page")
                .build();
        ListObjectsV2Response secondPage = ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("fairytales/7/page_1001.png").build())
                .isTruncated(false)
                .build();
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(firstPage, secondPage);

        client.deleteByPrefix("fairytales/7/");

        ArgumentCaptor<ListObjectsV2Request> listCaptor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client, times(2)).listObjectsV2(listCaptor.capture());
        assertThat(listCaptor.getAllValues().get(0).continuationToken()).isNull();
        assertThat(listCaptor.getAllValues().get(1).continuationToken()).isEqualTo("next-page");

        ArgumentCaptor<DeleteObjectsRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client, times(2)).deleteObjects(deleteCaptor.capture());
        assertThat(deleteCaptor.getAllValues().get(0).delete().objects()).hasSize(1000);
        assertThat(deleteCaptor.getAllValues().get(1).delete().objects())
                .extracting("key")
                .containsExactly("fairytales/7/page_1001.png");
    }
}
