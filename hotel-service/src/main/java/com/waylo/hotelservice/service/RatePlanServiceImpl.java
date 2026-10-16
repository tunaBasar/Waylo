package com.waylo.hotelservice.service;

import com.waylo.hotelservice.domain.CancellationPolicy;
import com.waylo.hotelservice.domain.CatalogStatus;
import com.waylo.hotelservice.domain.RatePlan;
import com.waylo.hotelservice.dto.RatePlanCreateRequest;
import com.waylo.hotelservice.dto.RatePlanResponse;
import com.waylo.hotelservice.dto.RatePlanUpdateRequest;
import com.waylo.hotelservice.exception.BusinessRuleException;
import com.waylo.hotelservice.exception.DuplicateResourceException;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.repository.HotelRepository;
import com.waylo.hotelservice.repository.RatePlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class RatePlanServiceImpl implements RatePlanService {

    private static final Logger log = LoggerFactory.getLogger(RatePlanServiceImpl.class);

    private static final int MAX_RATE_PLANS = 50;

    private final RatePlanRepository ratePlanRepository;
    private final HotelRepository hotelRepository;

    public RatePlanServiceImpl(RatePlanRepository ratePlanRepository, HotelRepository hotelRepository) {
        this.ratePlanRepository = ratePlanRepository;
        this.hotelRepository = hotelRepository;
    }

    private void ensureHotelExists(UUID hotelId) {
        if (!hotelRepository.existsById(hotelId)) {
            throw new ResourceNotFoundException("Hotel", "id", hotelId);
        }
    }

    private void ensureCancellationRule(CancellationPolicy policy, Integer freeDays) {
        if (policy == CancellationPolicy.FREE_CANCELLATION) {
            if (freeDays == null || freeDays < 0 || freeDays > 365) {
                throw new BusinessRuleException("CANCELLATION_RULE",
                        "Free cancellation requires freeCancellationDaysBefore between 0 and 365");
            }
        } else if (policy == CancellationPolicy.NON_REFUNDABLE) {
            if (freeDays != null) {
                throw new BusinessRuleException("CANCELLATION_RULE",
                        "NON_REFUNDABLE cancellation policy requires freeCancellationDaysBefore to be null");
            }
        }
    }

    private RatePlanResponse toResponse(RatePlan entity) {
        return new RatePlanResponse(
                entity.getId(),
                entity.getHotelId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getMealPlan(),
                entity.getCancellationPolicy(),
                entity.getFreeCancellationDaysBefore(),
                entity.getStatus(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    @Override
    public RatePlanResponse create(UUID hotelId, RatePlanCreateRequest request) {
        ensureHotelExists(hotelId);

        ratePlanRepository.lockHotelById(hotelId);

        if (ratePlanRepository.countByHotelId(hotelId) >= MAX_RATE_PLANS) {
            throw new BusinessRuleException("LIMIT_REACHED",
                    "Maximum of " + MAX_RATE_PLANS + " rate plans per hotel reached");
        }

        if (ratePlanRepository.existsByHotelIdAndCode(hotelId, request.code())) {
            throw new DuplicateResourceException("RatePlan", "code", request.code());
        }

        ensureCancellationRule(request.cancellationPolicy(), request.freeCancellationDaysBefore());

        RatePlan entity = new RatePlan();
        entity.setHotelId(hotelId);
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setMealPlan(request.mealPlan());
        entity.setCancellationPolicy(request.cancellationPolicy());
        entity.setFreeCancellationDaysBefore(request.freeCancellationDaysBefore());
        entity.setStatus(CatalogStatus.ACTIVE);

        entity = ratePlanRepository.saveAndFlush(entity);
        log.info("Rate plan created: id={}, hotelId={}", entity.getId(), hotelId);
        return toResponse(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public RatePlanResponse getById(UUID hotelId, UUID ratePlanId, boolean includeInactive) {
        ensureHotelExists(hotelId);

        RatePlan entity = ratePlanRepository.findByIdAndHotelId(ratePlanId, hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("RatePlan", "id", ratePlanId));

        if (!includeInactive && entity.getStatus() != CatalogStatus.ACTIVE) {
            throw new ResourceNotFoundException("RatePlan", "id", ratePlanId);
        }

        return toResponse(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RatePlanResponse> getByHotelId(UUID hotelId, boolean includeInactive) {
        ensureHotelExists(hotelId);

        List<RatePlan> entities;
        if (includeInactive) {
            entities = ratePlanRepository.findByHotelIdOrderByNameAsc(hotelId);
        } else {
            entities = ratePlanRepository.findByHotelIdAndStatusOrderByNameAsc(hotelId, CatalogStatus.ACTIVE);
        }
        return entities.stream().map(this::toResponse).toList();
    }

    @Override
    public RatePlanResponse update(UUID hotelId, UUID ratePlanId, RatePlanUpdateRequest request) {
        ensureHotelExists(hotelId);

        RatePlan entity = ratePlanRepository.findByIdAndHotelId(ratePlanId, hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("RatePlan", "id", ratePlanId));

        // Handle expectedVersion check
        if (request.expectedVersion() != null && !Objects.equals(request.expectedVersion(), entity.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(RatePlan.class, ratePlanId);
        }

        // Apply partial updates (null = unchanged)
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.mealPlan() != null) {
            entity.setMealPlan(request.mealPlan());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }

        // Handle cancellation policy update with rule enforcement
        if (request.cancellationPolicy() != null) {
            entity.setCancellationPolicy(request.cancellationPolicy());
            // If policy is being set to NON_REFUNDABLE, clear freeCancellationDaysBefore
            if (request.cancellationPolicy() == CancellationPolicy.NON_REFUNDABLE) {
                entity.setFreeCancellationDaysBefore(null);
            }
            // Immediate FREE_CANCELLATION validation: must have non-null days
            if (request.cancellationPolicy() == CancellationPolicy.FREE_CANCELLATION
                    && entity.getFreeCancellationDaysBefore() == null
                    && request.freeCancellationDaysBefore() == null) {
                throw new BusinessRuleException("CANCELLATION_RULE",
                        "FREE_CANCELLATION requires non-null freeCancellationDaysBefore");
            }
        }

        // Handle freeCancellationDaysBefore update
        if (request.freeCancellationDaysBefore() != null) {
            entity.setFreeCancellationDaysBefore(request.freeCancellationDaysBefore());
        }

        // Re-validate cancellation rule with current state
        ensureCancellationRule(entity.getCancellationPolicy(), entity.getFreeCancellationDaysBefore());

        entity = ratePlanRepository.saveAndFlush(entity);
        log.info("Rate plan updated: id={}, hotelId={}", ratePlanId, hotelId);
        return toResponse(entity);
    }
}
