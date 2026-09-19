import java.util.*;

// A classic LLD problem: vehicle-type polymorphism (a Strategy-shaped fit for spot allocation)
// plus a clean State-shaped fit for each spot's occupied/free lifecycle. The interview signal
// here is whether the design accommodates a NEW vehicle type or spot type without editing
// existing allocation logic - the same Open/Closed idea from the beginner tier, applied to a
// less trivial domain.
public class ParkingLotDemo {

    enum VehicleSize { MOTORCYCLE, CAR, TRUCK }

    record Vehicle(String licensePlate, VehicleSize size) {}

    sealed interface SpotState permits SpotState.Free, SpotState.Occupied {
        record Free() implements SpotState {}
        record Occupied(Vehicle vehicle) implements SpotState {}
    }

    static class ParkingSpot {
        final String id;
        final VehicleSize maxSize;
        SpotState state = new SpotState.Free();

        ParkingSpot(String id, VehicleSize maxSize) {
            this.id = id;
            this.maxSize = maxSize;
        }

        boolean canFit(Vehicle vehicle) {
            return state instanceof SpotState.Free && vehicle.size().ordinal() <= maxSize.ordinal();
        }

        void park(Vehicle vehicle) {
            if (!canFit(vehicle)) throw new IllegalStateException("Spot " + id + " cannot fit this vehicle");
            state = new SpotState.Occupied(vehicle);
        }

        void vacate() {
            state = new SpotState.Free();
        }
    }

    static class ParkingLot {
        private final List<ParkingSpot> spots;

        ParkingLot(List<ParkingSpot> spots) { this.spots = spots; }

        Optional<ParkingSpot> park(Vehicle vehicle) {
            // smallest-fitting-spot-first: don't waste a truck spot on a motorcycle
            return spots.stream()
                    .filter(s -> s.canFit(vehicle))
                    .min(Comparator.comparingInt(s -> s.maxSize.ordinal()))
                    .map(spot -> {
                        spot.park(vehicle);
                        return spot;
                    });
        }

        void vacate(String spotId) {
            spots.stream().filter(s -> s.id.equals(spotId)).findFirst().ifPresent(ParkingSpot::vacate);
        }

        long availableCount(VehicleSize size) {
            return spots.stream()
                    .filter(s -> s.state instanceof SpotState.Free && size.ordinal() <= s.maxSize.ordinal())
                    .count();
        }
    }

    public static void main(String[] args) {
        ParkingLot lot = new ParkingLot(List.of(
                new ParkingSpot("M1", VehicleSize.MOTORCYCLE),
                new ParkingSpot("C1", VehicleSize.CAR),
                new ParkingSpot("C2", VehicleSize.CAR),
                new ParkingSpot("T1", VehicleSize.TRUCK)
        ));

        Vehicle car = new Vehicle("CAR-123", VehicleSize.CAR);
        Vehicle bike = new Vehicle("BIKE-456", VehicleSize.MOTORCYCLE);
        Vehicle truck = new Vehicle("TRUCK-789", VehicleSize.TRUCK);

        lot.park(car).ifPresent(s -> System.out.println("Parked " + car.licensePlate() + " at " + s.id));
        lot.park(bike).ifPresent(s -> System.out.println("Parked " + bike.licensePlate() + " at " + s.id));
        lot.park(truck).ifPresent(s -> System.out.println("Parked " + truck.licensePlate() + " at " + s.id));

        System.out.println("Available CAR-or-smaller spots: " + lot.availableCount(VehicleSize.CAR));

        lot.vacate("C1");
        System.out.println("After vacating C1, available CAR-or-smaller spots: " + lot.availableCount(VehicleSize.CAR));

        Vehicle secondTruck = new Vehicle("TRUCK-999", VehicleSize.TRUCK);
        System.out.println("Can a second truck park? " + lot.park(secondTruck).isPresent()
                + " (T1 is taken, and a truck can't fit in a CAR or MOTORCYCLE spot)");
    }
}
