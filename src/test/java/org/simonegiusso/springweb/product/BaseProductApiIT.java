package org.simonegiusso.springweb.product;

import static org.simonegiusso.springweb.product.ProductController.BASE_PATH;

import org.simonegiusso.springweb.support.BaseApiIT;
import org.simonegiusso.springweb.support.ProductTestFactory;
import org.simonegiusso.springweb.support.TestProductRepository;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class BaseProductApiIT extends BaseApiIT {

    @Autowired
    protected ProductTestFactory testData;

    @Autowired
    protected TestProductRepository products;

    @Override
    protected String basePath() {
        return BASE_PATH;
    }
}
