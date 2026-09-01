#include <jni.h>

#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/errqueue.h>
#include <linux/icmp.h>
#include <netinet/in.h>
#include <poll.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <unistd.h>

#include <chrono>
#include <cstring>
#include <string>

#ifndef IP_RECVERR
#define IP_RECVERR 11
#endif

#ifndef MSG_ERRQUEUE
#define MSG_ERRQUEUE 0x2000
#endif

namespace {

constexpr int kStatusHop = 1;
constexpr int kStatusDestinationReached = 2;
constexpr int kStatusTimeout = 3;
constexpr int kStatusLocalError = 4;
constexpr int kStatusPermissionDenied = 5;
constexpr int kStatusUnsupported = 6;
constexpr int kStatusCancelled = 7;
constexpr int kStatusInvalidResponse = 8;

constexpr char kSocketCreate[] = "SOCKET_CREATE";
constexpr char kSetReceiveError[] = "SET_RECVERR";
constexpr char kSetTtl[] = "SET_TTL";
constexpr char kSendTo[] = "SENDTO";
constexpr char kPoll[] = "POLL";
constexpr char kRecvMsg[] = "RECVMSG";
constexpr char kParseError[] = "PARSE_ERROR";
constexpr char kCancel[] = "CANCEL";
constexpr char kPipeCreate[] = "CANCEL_PIPE";

long long monotonicMillis() {
    const auto now = std::chrono::steady_clock::now().time_since_epoch();
    return std::chrono::duration_cast<std::chrono::milliseconds>(now).count();
}

bool isPermissionError(int value) {
    return value == EPERM || value == EACCES;
}

bool isUnsupportedError(int value) {
    return value == ENOPROTOOPT || value == EOPNOTSUPP || value == EINVAL;
}

jstring nullableString(JNIEnv* environment, const char* value) {
    return value == nullptr ? nullptr : environment->NewStringUTF(value);
}

jobject newSocketResult(
    JNIEnv* environment,
    int socketFd,
    int cancelReadFd,
    int cancelWriteFd,
    const char* operation,
    int errorNumber
) {
    jclass resultClass = environment->FindClass(
        "com/networktoolbox/core/network/traceroute/NativeSocketOpenResult"
    );
    if (resultClass == nullptr) return nullptr;
    jmethodID constructor = environment->GetMethodID(
        resultClass,
        "<init>",
        "(IIILjava/lang/String;I)V"
    );
    if (constructor == nullptr) return nullptr;
    jstring operationString = nullableString(environment, operation);
    jobject result = environment->NewObject(
        resultClass,
        constructor,
        socketFd,
        cancelReadFd,
        cancelWriteFd,
        operationString,
        errorNumber
    );
    if (operationString != nullptr) environment->DeleteLocalRef(operationString);
    return result;
}

jobject newProbeResult(
    JNIEnv* environment,
    int status,
    const char* responder,
    long long latencyMs,
    int icmpType,
    int icmpCode,
    int errorNumber,
    const char* operation
) {
    jclass resultClass = environment->FindClass(
        "com/networktoolbox/core/network/traceroute/NativeProbeOutcome"
    );
    if (resultClass == nullptr) return nullptr;
    jmethodID constructor = environment->GetMethodID(
        resultClass,
        "<init>",
        "(ILjava/lang/String;JIIILjava/lang/String;)V"
    );
    if (constructor == nullptr) return nullptr;
    jstring responderString = nullableString(environment, responder);
    jstring operationString = nullableString(environment, operation);
    jobject result = environment->NewObject(
        resultClass,
        constructor,
        status,
        responderString,
        latencyMs,
        icmpType,
        icmpCode,
        errorNumber,
        operationString
    );
    if (responderString != nullptr) environment->DeleteLocalRef(responderString);
    if (operationString != nullptr) environment->DeleteLocalRef(operationString);
    return result;
}

std::string ipv4Text(const sockaddr_in& address) {
    char buffer[INET_ADDRSTRLEN] = {};
    if (inet_ntop(AF_INET, &address.sin_addr, buffer, sizeof(buffer)) == nullptr) {
        return {};
    }
    return buffer;
}

bool parseIpv4(const char* value, sockaddr_in* target) {
    if (value == nullptr || target == nullptr) return false;
    std::memset(target, 0, sizeof(*target));
    target->sin_family = AF_INET;
    return inet_pton(AF_INET, value, &target->sin_addr) == 1;
}

void closeFd(int* descriptor) {
    if (descriptor != nullptr && *descriptor >= 0) {
        close(*descriptor);
        *descriptor = -1;
    }
}

jobject openSocket(JNIEnv* environment) {
    const int socketFd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (socketFd < 0) {
        return newSocketResult(environment, -1, -1, -1, kSocketCreate, errno);
    }

    int enabled = 1;
    if (setsockopt(socketFd, IPPROTO_IP, IP_RECVERR, &enabled, sizeof(enabled)) != 0) {
        const int errorNumber = errno;
        close(socketFd);
        return newSocketResult(environment, -1, -1, -1, kSetReceiveError, errorNumber);
    }

    int cancelPipe[2] = {-1, -1};
    if (pipe2(cancelPipe, O_CLOEXEC | O_NONBLOCK) != 0) {
        const int errorNumber = errno;
        close(socketFd);
        return newSocketResult(environment, -1, -1, -1, kPipeCreate, errorNumber);
    }

    return newSocketResult(
        environment,
        socketFd,
        cancelPipe[0],
        cancelPipe[1],
        nullptr,
        -1
    );
}

jobject probe(
    JNIEnv* environment,
    int socketFd,
    int cancelReadFd,
    const char* destination,
    int ttl,
    int destinationPort,
    int timeoutMs
) {
    sockaddr_in target = {};
    if (socketFd < 0 || cancelReadFd < 0 || !parseIpv4(destination, &target)) {
        return newProbeResult(
            environment,
            kStatusLocalError,
            nullptr,
            -1,
            -1,
            -1,
            EINVAL,
            kSendTo
        );
    }
    if (ttl < 1 || ttl > 255 || destinationPort < 1 || destinationPort > 65535 ||
        timeoutMs < 1 || timeoutMs > 60000) {
        return newProbeResult(
            environment,
            kStatusLocalError,
            nullptr,
            -1,
            -1,
            -1,
            EINVAL,
            kSetTtl
        );
    }

    if (setsockopt(socketFd, IPPROTO_IP, IP_TTL, &ttl, sizeof(ttl)) != 0) {
        const int errorNumber = errno;
        const int status = isPermissionError(errorNumber)
            ? kStatusPermissionDenied
            : (isUnsupportedError(errorNumber) ? kStatusUnsupported : kStatusLocalError);
        return newProbeResult(environment, status, nullptr, -1, -1, -1, errorNumber, kSetTtl);
    }

    constexpr char payload[] = "nt-trace";
    target.sin_port = htons(static_cast<uint16_t>(destinationPort));
    const long long sentAt = monotonicMillis();
    const ssize_t sent = sendto(
        socketFd,
        payload,
        sizeof(payload) - 1,
        0,
        reinterpret_cast<const sockaddr*>(&target),
        sizeof(target)
    );
    if (sent < 0) {
        const int errorNumber = errno;
        const int status = isPermissionError(errorNumber)
            ? kStatusPermissionDenied
            : kStatusLocalError;
        return newProbeResult(environment, status, nullptr, -1, -1, -1, errorNumber, kSendTo);
    }

    pollfd descriptors[2] = {
        {socketFd, POLLERR | POLLIN, 0},
        {cancelReadFd, POLLIN, 0},
    };
    const int pollResult = poll(descriptors, 2, timeoutMs);
    if (pollResult == 0) {
        return newProbeResult(environment, kStatusTimeout, nullptr, -1, -1, -1, 0, nullptr);
    }
    if (pollResult < 0) {
        const int errorNumber = errno;
        return newProbeResult(environment, kStatusLocalError, nullptr, -1, -1, -1, errorNumber, kPoll);
    }
    if ((descriptors[1].revents & POLLIN) != 0) {
        char cancelled = 0;
        (void)read(cancelReadFd, &cancelled, sizeof(cancelled));
        return newProbeResult(environment, kStatusCancelled, nullptr, -1, -1, -1, 0, kCancel);
    }
    if ((descriptors[0].revents & (POLLERR | POLLIN)) == 0) {
        return newProbeResult(
            environment,
            kStatusLocalError,
            nullptr,
            -1,
            -1,
            -1,
            0,
            kPoll
        );
    }

    char data[512] = {};
    char control[512] = {};
    sockaddr_storage peer = {};
    iovec vector = {data, sizeof(data)};
    msghdr message = {};
    message.msg_name = &peer;
    message.msg_namelen = sizeof(peer);
    message.msg_iov = &vector;
    message.msg_iovlen = 1;
    message.msg_control = control;
    message.msg_controllen = sizeof(control);

    const ssize_t received = recvmsg(socketFd, &message, MSG_ERRQUEUE | MSG_DONTWAIT);
    if (received < 0) {
        const int errorNumber = errno;
        if (errorNumber == EAGAIN || errorNumber == EWOULDBLOCK) {
            return newProbeResult(environment, kStatusTimeout, nullptr, -1, -1, -1, errorNumber, nullptr);
        }
        const int status = isPermissionError(errorNumber)
            ? kStatusPermissionDenied
            : kStatusLocalError;
        return newProbeResult(environment, status, nullptr, -1, -1, -1, errorNumber, kRecvMsg);
    }

    bool foundExtendedError = false;
    bool malformedControl = (message.msg_flags & MSG_CTRUNC) != 0;
    std::string responder;
    int icmpType = -1;
    int icmpCode = -1;
    int errorNumber = -1;
    int origin = -1;
    for (cmsghdr* header = CMSG_FIRSTHDR(&message);
         header != nullptr;
         header = CMSG_NXTHDR(&message, header)) {
        if (header->cmsg_level != IPPROTO_IP || header->cmsg_type != IP_RECVERR) continue;
        if (header->cmsg_len < CMSG_LEN(sizeof(sock_extended_err))) {
            malformedControl = true;
            continue;
        }

        const auto* extended = reinterpret_cast<const sock_extended_err*>(CMSG_DATA(header));
        foundExtendedError = true;
        origin = extended->ee_origin;
        icmpType = extended->ee_type;
        icmpCode = extended->ee_code;
        errorNumber = static_cast<int>(extended->ee_errno);

        const size_t dataLength = header->cmsg_len - CMSG_LEN(0);
        if (dataLength >= sizeof(sock_extended_err) + sizeof(sockaddr_in)) {
            const auto* offender = SO_EE_OFFENDER(extended);
            if (offender != nullptr && offender->sa_family == AF_INET) {
                const auto* offenderIpv4 = reinterpret_cast<const sockaddr_in*>(offender);
                responder = ipv4Text(*offenderIpv4);
            }
        }
    }

    if (malformedControl || !foundExtendedError) {
        return newProbeResult(
            environment,
            kStatusInvalidResponse,
            responder.empty() ? nullptr : responder.c_str(),
            monotonicMillis() - sentAt,
            icmpType,
            icmpCode,
            errorNumber,
            kParseError
        );
    }

    if (origin == SO_EE_ORIGIN_ICMP && icmpType == ICMP_TIME_EXCEEDED) {
        return newProbeResult(
            environment,
            kStatusHop,
            responder.empty() ? nullptr : responder.c_str(),
            monotonicMillis() - sentAt,
            icmpType,
            icmpCode,
            errorNumber,
            nullptr
        );
    }
    if (origin == SO_EE_ORIGIN_ICMP && icmpType == ICMP_DEST_UNREACH &&
        icmpCode == ICMP_PORT_UNREACH) {
        return newProbeResult(
            environment,
            kStatusDestinationReached,
            responder.empty() ? nullptr : responder.c_str(),
            monotonicMillis() - sentAt,
            icmpType,
            icmpCode,
            errorNumber,
            nullptr
        );
    }
    return newProbeResult(
        environment,
        kStatusLocalError,
        responder.empty() ? nullptr : responder.c_str(),
        monotonicMillis() - sentAt,
        icmpType,
        icmpCode,
        errorNumber,
        "ICMP_OTHER"
    );
}

}  // namespace

extern "C" JNIEXPORT jobject JNICALL
Java_com_networktoolbox_core_network_data_traceroute_NativeUdpTracerouteJni_open(
    JNIEnv* environment,
    jobject
) {
    return openSocket(environment);
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_networktoolbox_core_network_data_traceroute_NativeUdpTracerouteJni_probe(
    JNIEnv* environment,
    jobject,
    jint socketFd,
    jint cancelReadFd,
    jstring destination,
    jint ttl,
    jint destinationPort,
    jint timeoutMs
) {
    if (destination == nullptr) {
        return newProbeResult(environment, kStatusLocalError, nullptr, -1, -1, -1, EINVAL, kSendTo);
    }
    const char* rawDestination = environment->GetStringUTFChars(destination, nullptr);
    if (rawDestination == nullptr) {
        return newProbeResult(environment, kStatusLocalError, nullptr, -1, -1, -1, EINVAL, kSendTo);
    }
    const jobject result = probe(
        environment,
        socketFd,
        cancelReadFd,
        rawDestination,
        ttl,
        destinationPort,
        timeoutMs
    );
    environment->ReleaseStringUTFChars(destination, rawDestination);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_networktoolbox_core_network_data_traceroute_NativeUdpTracerouteJni_cancel(
    JNIEnv*,
    jobject,
    jint cancelWriteFd
) {
    if (cancelWriteFd < 0) return;
    const char value = 1;
    (void)write(cancelWriteFd, &value, sizeof(value));
}

extern "C" JNIEXPORT void JNICALL
Java_com_networktoolbox_core_network_data_traceroute_NativeUdpTracerouteJni_close(
    JNIEnv*,
    jobject,
    jint socketFd,
    jint cancelReadFd,
    jint cancelWriteFd
) {
    int socketDescriptor = socketFd;
    int cancelReadDescriptor = cancelReadFd;
    int cancelWriteDescriptor = cancelWriteFd;
    closeFd(&socketDescriptor);
    closeFd(&cancelReadDescriptor);
    closeFd(&cancelWriteDescriptor);
}
