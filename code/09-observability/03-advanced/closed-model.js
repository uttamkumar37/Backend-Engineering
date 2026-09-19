import http from 'k6/http';

// CLOSED MODEL: a fixed number of virtual users, each one waiting for its response before
// sending the next request. If the target slows down, each VU just sends requests LESS OFTEN -
// the achieved request rate self-limits, hiding how bad a real overload would actually be.
export const options = {
    vus: 25,   // same "25" as the open model's arrival rate, so the comparison is apples-to-apples
    duration: '10s',
};

export default function () {
    http.get('http://localhost:8090/bottleneck');
}
