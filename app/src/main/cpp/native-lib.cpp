#include <jni.h>
#include <opencv2/opencv.hpp>
#include <jni.h>
#include <jni.h>
#include <jni.h>

using namespace cv;


extern "C"
JNIEXPORT void JNICALL
Java_com_example_teamproject6_CameraActivity_convertMatToArray(JNIEnv *env, jobject thiz,
                                                               jobject mat_addr, jbyteArray array) {
    // TODO: implement convertMatToArray()
    Mat &mat = *(Mat *)mat_addr;
    env->SetByteArrayRegion(array,0,mat.total(),(const jbyte *)mat.data);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_example_teamproject6_CameraActivity_ConvertRGBtoGray(JNIEnv *env, jobject thiz,
                                                              jlong mat_addr_input,
                                                              jlong mat_addr_result) {
    Mat &matInput = *(Mat *)mat_addr_input;
    Mat &matResult = *(Mat *)mat_addr_result;

    cvtColor(matInput, matResult, COLOR_RGBA2GRAY);
}