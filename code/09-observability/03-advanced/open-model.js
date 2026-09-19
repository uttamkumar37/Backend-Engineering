import http from 'k6/http';

// OPEN MODEL: a constant arrival rate, independent of how slow the target responds - matching
// how real external traffic actually behaves (new requests keep arriving regardless of your
// current latency). This is what actually reveals queueing/latency blow-up under overload.
export const options = {
    scenarios: {
        constant_arrivals: {
            executor: 'constant-arrival-rate',
            rate: 25,              // ABOVE the server's true ~15 req/s capacity (3 slots / 200ms) -
                                    // on purpose, to reveal the queueing a closed model would hide
            timeUnit: '1s',
            duration: '10s',
            preAllocatedVUs: 30,   // enough VUs to actually sustain the arrival rate under queueing
            maxVUs: 60,
        },
    },
};

export default function () {
    http.get('http://localhost:8090/bottleneck');
}
