package com.qrmenu.restaurant;

import com.qrmenu.common.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;

    public RestaurantService(RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
    }

    public Restaurant create(String name) {
        Restaurant restaurant = new Restaurant(name);
        return restaurantRepository.save(restaurant);
    }

    public Restaurant rename(UUID id, String newName) {
        Restaurant restaurant = getOrThrow(id);
        restaurant.rename(newName);
        return restaurantRepository.save(restaurant);
    }

    public Restaurant getOrThrow(UUID id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Restaurant introuvable: " + id));
    }

    public List<Restaurant> findAll() {
        return restaurantRepository.findAll();
    }
}
